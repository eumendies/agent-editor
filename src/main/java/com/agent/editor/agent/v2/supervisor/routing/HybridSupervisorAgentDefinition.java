package com.agent.editor.agent.v2.supervisor.routing;

import com.agent.editor.agent.v2.supervisor.SupervisorAgentDefinition;
import com.agent.editor.agent.v2.supervisor.SupervisorContext;
import com.agent.editor.agent.v2.supervisor.SupervisorDecision;
import com.agent.editor.agent.v2.supervisor.worker.ReviewerFeedback;
import com.agent.editor.agent.v2.supervisor.worker.ReviewerVerdict;
import com.agent.editor.agent.v2.supervisor.worker.WorkerDefinition;
import com.agent.editor.agent.v2.supervisor.worker.WorkerResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HybridSupervisorAgentDefinition implements SupervisorAgentDefinition {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final SupervisorRoutingAiService routingAiService;

    public HybridSupervisorAgentDefinition(ChatModel chatModel) {
        this(createRoutingAiService(chatModel));
    }

    HybridSupervisorAgentDefinition(SupervisorRoutingAiService routingAiService) {
        this.routingAiService = routingAiService;
    }

    @Override
    public SupervisorDecision decide(SupervisorContext context) {
        ReviewerFeedback reviewerFeedback = latestReviewerFeedback(context);
        if (reviewerFeedback != null
                && reviewerFeedback.getVerdict() == ReviewerVerdict.PASS
                && reviewerFeedback.isInstructionSatisfied()
                && reviewerFeedback.isEvidenceGrounded()) {
            return new SupervisorDecision.Complete(
                    context.getCurrentContent(),
                    summarize(context),
                    "reviewer approved completion"
            );
        }

        if (shouldStopForNoProgress(context)) {
            return new SupervisorDecision.Complete(
                    context.getCurrentContent(),
                    summarize(context),
                    "no progress detected"
            );
        }

        // 这里只保留硬规则过滤，避免用不可靠的关键词意图判断提前排除 worker。
        List<WorkerDefinition> candidates = selectCandidates(context);
        if (candidates.isEmpty()) {
            return new SupervisorDecision.Complete(
                    context.getCurrentContent(),
                    summarize(context),
                    "no candidate workers remain"
            );
        }

        SupervisorRoutingResponse routingResponse = requestModelDecision(context, candidates);
        if (routingResponse != null) {
            if (routingResponse.getAction() == SupervisorAction.COMPLETE) {
                return new SupervisorDecision.Complete(
                        firstNonBlank(routingResponse.getFinalContent(), context.getCurrentContent()),
                        firstNonBlank(routingResponse.getSummary(), summarize(context)),
                        firstNonBlank(routingResponse.getReasoning(), "model requested completion")
                );
            }

            if (routingResponse.getAction() == SupervisorAction.ASSIGN_WORKER) {
                // 模型只能选择候选集合内的 worker，越界结果直接视为无效输出。
                WorkerDefinition selectedWorker = candidates.stream()
                        .filter(worker -> worker.getWorkerId().equals(routingResponse.getWorkerId()))
                        .findFirst()
                        .orElse(null);
                if (selectedWorker != null) {
                    return new SupervisorDecision.AssignWorker(
                            selectedWorker.getWorkerId(),
                            firstNonBlank(routingResponse.getInstruction(), buildFallbackInstruction(selectedWorker, context)),
                            firstNonBlank(routingResponse.getReasoning(), "model selected candidate")
                    );
                }
            }
        }

        WorkerDefinition fallbackWorker = candidates.get(0);
        return new SupervisorDecision.AssignWorker(
                fallbackWorker.getWorkerId(),
                buildFallbackInstruction(fallbackWorker, context),
                "rule-based fallback"
        );
    }

    private static SupervisorRoutingAiService createRoutingAiService(ChatModel chatModel) {
        return AiServices.builder(SupervisorRoutingAiService.class)
                .chatModel(chatModel)
                .build();
    }

    private List<WorkerDefinition> selectCandidates(SupervisorContext context) {
        String demotedWorkerId = consecutiveWorkerId(context);
        List<WorkerDefinition> allowedWorkers = context.getAvailableWorkers().stream()
                .filter(worker -> !worker.getWorkerId().equals(demotedWorkerId))
                .toList();
        if (allowedWorkers.isEmpty()) {
            return List.of();
        }

        ArrayList<WorkerDefinition> ordered = new ArrayList<>();
        if (context.getWorkerResults().isEmpty()) {
            addRemaining(ordered, allowedWorkers);
            return List.copyOf(ordered);
        }

        WorkerResult latest = context.getWorkerResults().get(context.getWorkerResults().size() - 1);
        if ("reviewer".equals(latest.getWorkerId())) {
            ReviewerFeedback reviewerFeedback = parseReviewerFeedback(latest.getSummary());
            if (reviewerFeedback != null) {
                // reviewer 只报告问题类型，不直接命令下一跳；是否重新 research 仍由 supervisor 控制。
                if (!reviewerFeedback.isEvidenceGrounded()) {
                    addByCapability(ordered, allowedWorkers, "research");
                    addByCapability(ordered, allowedWorkers, "write");
                } else if (!reviewerFeedback.isInstructionSatisfied()
                        || !reviewerFeedback.getMissingRequirements().isEmpty()) {
                    addByCapability(ordered, allowedWorkers, "write");
                }
            }
        }

        addRemaining(ordered, allowedWorkers);
        return List.copyOf(ordered);
    }

    private boolean shouldStopForNoProgress(SupervisorContext context) {
        if (context.getWorkerResults().size() < 2) {
            return false;
        }

        WorkerResult latest = context.getWorkerResults().get(context.getWorkerResults().size() - 1);
        WorkerResult previous = context.getWorkerResults().get(context.getWorkerResults().size() - 2);
        return Objects.equals(latest.getUpdatedContent(), context.getCurrentContent())
                && Objects.equals(previous.getUpdatedContent(), context.getCurrentContent())
                && Objects.equals(latest.getUpdatedContent(), previous.getUpdatedContent())
                && Objects.equals(latest.getSummary(), previous.getSummary());
    }

    private String consecutiveWorkerId(SupervisorContext context) {
        if (context.getWorkerResults().size() < 2) {
            return null;
        }

        WorkerResult latest = context.getWorkerResults().get(context.getWorkerResults().size() - 1);
        WorkerResult previous = context.getWorkerResults().get(context.getWorkerResults().size() - 2);
        if (latest.getWorkerId().equals(previous.getWorkerId())) {
            // 若重复调用2次, 下一次不再调用该worker
            return latest.getWorkerId();
        }
        return null;
    }

    private SupervisorRoutingResponse requestModelDecision(SupervisorContext context, List<WorkerDefinition> candidates) {
        if (routingAiService == null) {
            return null;
        }

        try {
            return routingAiService.route(
                    context.getOriginalInstruction(),
                    context.getCurrentContent(),
                    renderCandidates(candidates),
                    renderWorkerResults(context.getWorkerResults())
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String renderCandidates(List<WorkerDefinition> candidates) {
        return candidates.stream()
                .map(worker -> worker.getWorkerId()
                        + " | role=" + worker.getRole()
                        + " | description=" + worker.getDescription()
                        + " | capabilities=" + String.join(", ", worker.getCapabilities()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("No candidate workers");
    }

    private String renderWorkerResults(List<WorkerResult> workerResults) {
        if (workerResults.isEmpty()) {
            return "No worker steps executed";
        }
        return workerResults.stream()
                .map(result -> result.getWorkerId() + ": " + result.getSummary())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("No worker steps executed");
    }

    private String buildFallbackInstruction(WorkerDefinition worker, SupervisorContext context) {
        return worker.getRole() + ": " + worker.getDescription() + "\nTask: " + context.getOriginalInstruction();
    }

    private String summarize(SupervisorContext context) {
        if (context.getWorkerResults().isEmpty()) {
            return "No worker steps executed";
        }
        return context.getWorkerResults().stream()
                .map(result -> result.getWorkerId() + ": " + result.getSummary())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("No worker steps executed");
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return Objects.requireNonNullElse(fallback, "");
    }

    private ReviewerFeedback latestReviewerFeedback(SupervisorContext context) {
        if (context.getWorkerResults().isEmpty()) {
            return null;
        }
        WorkerResult latest = context.getWorkerResults().get(context.getWorkerResults().size() - 1);
        if (!"reviewer".equals(latest.getWorkerId())) {
            return null;
        }
        return parseReviewerFeedback(latest.getSummary());
    }

    private ReviewerFeedback parseReviewerFeedback(String summary) {
        if (summary == null || summary.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(summary, ReviewerFeedback.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void addByCapability(List<WorkerDefinition> ordered,
                                 List<WorkerDefinition> allowedWorkers,
                                 String capability) {
        allowedWorkers.stream()
                .filter(worker -> worker.getCapabilities().contains(capability))
                .forEach(worker -> addIfAbsent(ordered, worker));
    }

    private void addRemaining(List<WorkerDefinition> ordered, List<WorkerDefinition> allowedWorkers) {
        allowedWorkers.forEach(worker -> addIfAbsent(ordered, worker));
    }

    private void addIfAbsent(List<WorkerDefinition> ordered, WorkerDefinition worker) {
        if (!ordered.contains(worker)) {
            ordered.add(worker);
        }
    }
}
