package com.agent.editor.agent.v2.supervisor;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

import java.util.List;
import java.util.Objects;

public class HybridSupervisorAgentDefinition implements SupervisorAgentDefinition {

    private final SupervisorRoutingAiService routingAiService;

    public HybridSupervisorAgentDefinition(ChatModel chatModel) {
        this(createRoutingAiService(chatModel));
    }

    HybridSupervisorAgentDefinition(SupervisorRoutingAiService routingAiService) {
        this.routingAiService = routingAiService;
    }

    @Override
    public SupervisorDecision decide(SupervisorContext context) {
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
        return context.availableWorkers().stream()
                .filter(worker -> !worker.workerId().equals(demotedWorkerId))
                .toList();
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
}
