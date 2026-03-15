package com.agent.editor.agent.v2.supervisor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class HybridSupervisorAgentDefinition implements SupervisorAgentDefinition {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HybridSupervisorAgentDefinition(ChatModel chatModel) {
        this.chatModel = chatModel;
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

        // 先用稳定规则缩小候选，再让模型只在有限集合里做选择。
        List<WorkerDefinition> candidates = selectCandidates(context);
        if (candidates.isEmpty()) {
            return new SupervisorDecision.Complete(
                    context.currentContent(),
                    summarize(context),
                    "no candidate workers remain"
            );
        }

        ModelDecision modelDecision = requestModelDecision(context, candidates);
        if (modelDecision != null) {
            if ("complete".equals(modelDecision.action())) {
                return new SupervisorDecision.Complete(
                        firstNonBlank(modelDecision.finalContent(), context.currentContent()),
                        firstNonBlank(modelDecision.summary(), summarize(context)),
                        firstNonBlank(modelDecision.reasoning(), "model requested completion")
                );
            }

            if ("assign_worker".equals(modelDecision.action())) {
                // 模型只能选择候选集合内的 worker，越界结果直接视为无效输出。
                WorkerDefinition selectedWorker = candidates.stream()
                        .filter(worker -> worker.workerId().equals(modelDecision.workerId()))
                        .findFirst()
                        .orElse(null);
                if (selectedWorker != null) {
                    return new SupervisorDecision.AssignWorker(
                            selectedWorker.workerId(),
                            firstNonBlank(modelDecision.instruction(), buildFallbackInstruction(selectedWorker, context)),
                            firstNonBlank(modelDecision.reasoning(), "model selected candidate")
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

    private List<WorkerDefinition> selectCandidates(SupervisorContext context) {
        String demotedWorkerId = consecutiveWorkerId(context);
        List<WorkerDefinition> remainingWorkers = context.availableWorkers().stream()
                .filter(worker -> !worker.workerId().equals(demotedWorkerId))
                .toList();
        if (remainingWorkers.isEmpty()) {
            return List.of();
        }

        if (context.workerResults().isEmpty()) {
            // 首轮优先把“先检查再动手”的请求导向 analyzer，避免直接进入 edit。
            List<WorkerDefinition> analyzers = byCapability(remainingWorkers, "analyze");
            if (needsInspection(context.originalInstruction()) && !analyzers.isEmpty()) {
                return analyzers;
            }
        }

        if (needsEditing(context.originalInstruction())) {
            List<WorkerDefinition> editors = byCapability(remainingWorkers, "edit");
            if (!editors.isEmpty()) {
                return editors;
            }
        }

        if (needsReview(context.originalInstruction())) {
            List<WorkerDefinition> reviewers = byCapability(remainingWorkers, "review");
            if (!reviewers.isEmpty()) {
                return reviewers;
            }
        }

        return remainingWorkers;
    }

    private List<WorkerDefinition> byCapability(List<WorkerDefinition> workers, String capability) {
        List<WorkerDefinition> matches = new ArrayList<>();
        for (WorkerDefinition worker : workers) {
            if (worker.capabilities().contains(capability)) {
                matches.add(worker);
            }
        }
        return matches;
    }

    private boolean needsInspection(String instruction) {
        String lower = normalize(instruction);
        return lower.contains("inspect")
                || lower.contains("analy")
                || lower.contains("understand")
                || lower.contains("assess")
                || lower.contains("before making changes");
    }

    private boolean needsEditing(String instruction) {
        String lower = normalize(instruction);
        return lower.contains("edit")
                || lower.contains("revise")
                || lower.contains("rewrite")
                || lower.contains("change")
                || lower.contains("improve")
                || lower.contains("fix");
    }

    private boolean needsReview(String instruction) {
        String lower = normalize(instruction);
        return lower.contains("review")
                || lower.contains("check")
                || lower.contains("verify");
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
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
            return latest.workerId();
        }
        return null;
    }

    private ModelDecision requestModelDecision(SupervisorContext context, List<WorkerDefinition> candidates) {
        if (chatModel == null) {
            return null;
        }

        try {
            String systemPrompt = """
                    You are a supervisor for a document editing workflow.
                    Choose one of the candidate workers or complete the task.
                    Return JSON with action, worker_id, instruction, summary, final_content, and reasoning.
                    """;
            String userPrompt = """
                    Original instruction:
                    %s

                    Current content:
                    %s

                    Candidate workers:
                    %s

                    Previous worker results:
                    %s
                    """.formatted(
                    context.originalInstruction(),
                    context.currentContent(),
                    candidates.stream()
                            .map(worker -> worker.workerId() + " (" + String.join(", ", worker.capabilities()) + ")")
                            .toList(),
                    context.workerResults().stream()
                            .map(result -> result.workerId() + ": " + result.summary())
                            .toList()
            );

            // supervisor 只消费结构化 JSON，解析失败时由上层统一走规则兜底。
            ChatResponse response = chatModel.chat(ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(systemPrompt),
                            UserMessage.from(userPrompt)
                    ))
                    .build());

            AiMessage aiMessage = response.aiMessage();
            if (aiMessage == null || aiMessage.text() == null || aiMessage.text().isBlank()) {
                return null;
            }

            JsonNode node = objectMapper.readTree(aiMessage.text());
            return new ModelDecision(
                    text(node, "action"),
                    text(node, "worker_id"),
                    text(node, "instruction"),
                    text(node, "summary"),
                    text(node, "final_content"),
                    text(node, "reasoning")
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
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

    private record ModelDecision(
            String action,
            String workerId,
            String instruction,
            String summary,
            String finalContent,
            String reasoning
    ) {
    }
}
