package com.agent.editor.agent.v2.supervisor;

import com.agent.editor.agent.v2.rag.ReviewerFeedback;
import com.agent.editor.agent.v2.rag.ReviewerVerdict;
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
                && reviewerFeedback.verdict() == ReviewerVerdict.PASS
                && reviewerFeedback.instructionSatisfied()
                && reviewerFeedback.evidenceGrounded()) {
            return new SupervisorDecision.Complete(
                    context.currentContent(),
                    summarize(context),
                    "reviewer approved completion"
            );
        }

        if (shouldStopForNoProgress(context)) {
            return new SupervisorDecision.Complete(
                    context.currentContent(),
                    summarize(context),
                    "no progress detected"
            );
        }

        // 这里只保留硬规则过滤，避免用不可靠的关键词意图判断提前排除 worker。
        List<WorkerDefinition> candidates = selectCandidates(context);
        if (candidates.isEmpty()) {
            return new SupervisorDecision.Complete(
                    context.currentContent(),
                    summarize(context),
                    "no candidate workers remain"
            );
        }

        SupervisorRoutingResponse routingResponse = requestModelDecision(context, candidates);
        if (routingResponse != null) {
            if (routingResponse.action() == SupervisorAction.COMPLETE) {
                return new SupervisorDecision.Complete(
                        firstNonBlank(routingResponse.finalContent(), context.currentContent()),
                        firstNonBlank(routingResponse.summary(), summarize(context)),
                        firstNonBlank(routingResponse.reasoning(), "model requested completion")
                );
            }

            if (routingResponse.action() == SupervisorAction.ASSIGN_WORKER) {
                // 模型只能选择候选集合内的 worker，越界结果直接视为无效输出。
                WorkerDefinition selectedWorker = candidates.stream()
                        .filter(worker -> worker.workerId().equals(routingResponse.workerId()))
                        .findFirst()
                        .orElse(null);
                if (selectedWorker != null) {
                    return new SupervisorDecision.AssignWorker(
                            selectedWorker.workerId(),
                            firstNonBlank(routingResponse.instruction(), buildFallbackInstruction(selectedWorker, context)),
                            firstNonBlank(routingResponse.reasoning(), "model selected candidate")
                    );
                }
            }
        }

        WorkerDefinition fallbackWorker = candidates.get(0);
        return new SupervisorDecision.AssignWorker(
                fallbackWorker.workerId(),
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
        List<WorkerDefinition> allowedWorkers = context.availableWorkers().stream()
                .filter(worker -> !worker.workerId().equals(demotedWorkerId))
                .toList();
        if (allowedWorkers.isEmpty()) {
            return List.of();
        }

        ArrayList<WorkerDefinition> ordered = new ArrayList<>();
        if (context.workerResults().isEmpty()) {
            if (needsResearch(context.originalInstruction())) {
                addByCapability(ordered, allowedWorkers, "research");
                addByCapability(ordered, allowedWorkers, "write");
            } else {
                addByCapability(ordered, allowedWorkers, "write");
                addByCapability(ordered, allowedWorkers, "research");
            }
            addRemaining(ordered, allowedWorkers);
            return List.copyOf(ordered);
        }

        WorkerResult latest = context.workerResults().get(context.workerResults().size() - 1);
        if ("reviewer".equals(latest.workerId())) {
            ReviewerFeedback reviewerFeedback = parseReviewerFeedback(latest.summary());
            if (reviewerFeedback != null) {
                // reviewer 只报告问题类型，不直接命令下一跳；是否重新 research 仍由 supervisor 控制。
                if (!reviewerFeedback.evidenceGrounded()) {
                    addByCapability(ordered, allowedWorkers, "research");
                    addByCapability(ordered, allowedWorkers, "write");
                } else if (!reviewerFeedback.instructionSatisfied()
                        || !reviewerFeedback.missingRequirements().isEmpty()) {
                    addByCapability(ordered, allowedWorkers, "write");
                }
            }
        }

        addRemaining(ordered, allowedWorkers);
        return List.copyOf(ordered);
    }

    private boolean shouldStopForNoProgress(SupervisorContext context) {
        if (context.workerResults().size() < 2) {
            return false;
        }

        WorkerResult latest = context.workerResults().get(context.workerResults().size() - 1);
        WorkerResult previous = context.workerResults().get(context.workerResults().size() - 2);
        return Objects.equals(latest.updatedContent(), context.currentContent())
                && Objects.equals(previous.updatedContent(), context.currentContent())
                && Objects.equals(latest.updatedContent(), previous.updatedContent())
                && Objects.equals(latest.summary(), previous.summary());
    }

    private String consecutiveWorkerId(SupervisorContext context) {
        if (context.workerResults().size() < 2) {
            return null;
        }

        WorkerResult latest = context.workerResults().get(context.workerResults().size() - 1);
        WorkerResult previous = context.workerResults().get(context.workerResults().size() - 2);
        if (latest.workerId().equals(previous.workerId())) {
            // 若重复调用2次, 下一次不再调用该worker
            return latest.workerId();
        }
        return null;
    }

    private SupervisorRoutingResponse requestModelDecision(SupervisorContext context, List<WorkerDefinition> candidates) {
        if (routingAiService == null) {
            return null;
        }

        try {
            return routingAiService.route(
                    context.originalInstruction(),
                    context.currentContent(),
                    renderCandidates(candidates),
                    renderWorkerResults(context.workerResults())
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String renderCandidates(List<WorkerDefinition> candidates) {
        return candidates.stream()
                .map(worker -> worker.workerId()
                        + " | role=" + worker.role()
                        + " | description=" + worker.description()
                        + " | capabilities=" + String.join(", ", worker.capabilities()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("No candidate workers");
    }

    private String renderWorkerResults(List<WorkerResult> workerResults) {
        if (workerResults.isEmpty()) {
            return "No worker steps executed";
        }
        return workerResults.stream()
                .map(result -> result.workerId() + ": " + result.summary())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("No worker steps executed");
    }

    private String buildFallbackInstruction(WorkerDefinition worker, SupervisorContext context) {
        return worker.role() + ": " + worker.description() + "\nTask: " + context.originalInstruction();
    }

    private String summarize(SupervisorContext context) {
        if (context.workerResults().isEmpty()) {
            return "No worker steps executed";
        }
        return context.workerResults().stream()
                .map(result -> result.workerId() + ": " + result.summary())
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
        if (context.workerResults().isEmpty()) {
            return null;
        }
        WorkerResult latest = context.workerResults().get(context.workerResults().size() - 1);
        if (!"reviewer".equals(latest.workerId())) {
            return null;
        }
        return parseReviewerFeedback(latest.summary());
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

    private boolean needsResearch(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            return false;
        }
        String normalized = instruction.toLowerCase();
        if (normalized.contains("without adding new facts")
                || normalized.contains("polish")
                || normalized.contains("rewrite")
                || normalized.contains("concise")
                || normalized.contains("tone")
                || normalized.contains("格式")
                || normalized.contains("润色")
                || normalized.contains("改写")) {
            return false;
        }
        return normalized.contains("knowledge base")
                || normalized.contains("knowledge")
                || normalized.contains("facts")
                || normalized.contains("project")
                || normalized.contains("grounded")
                || normalized.contains("technical")
                || normalized.contains("资料")
                || normalized.contains("事实")
                || normalized.contains("项目");
    }

    private void addByCapability(List<WorkerDefinition> ordered,
                                 List<WorkerDefinition> allowedWorkers,
                                 String capability) {
        allowedWorkers.stream()
                .filter(worker -> worker.capabilities().contains(capability))
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
