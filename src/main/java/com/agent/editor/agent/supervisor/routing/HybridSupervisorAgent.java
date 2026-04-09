package com.agent.editor.agent.supervisor.routing;

import com.agent.editor.agent.core.agent.AgentType;
import com.agent.editor.agent.core.agent.SupervisorAgent;
import com.agent.editor.agent.core.context.SupervisorContext;
import com.agent.editor.agent.core.agent.SupervisorDecision;
import com.agent.editor.agent.core.context.ModelInvocationContext;
import com.agent.editor.agent.supervisor.SupervisorContextFactory;
import com.agent.editor.agent.supervisor.SupervisorWorkerIds;
import com.agent.editor.agent.supervisor.worker.ReviewerFeedback;
import com.agent.editor.agent.supervisor.worker.ReviewerVerdict;
import com.agent.editor.agent.memory.ObservedTokenUsageRecorder;
import com.agent.editor.agent.util.StructuredOutputParsers;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 混合式 supervisor。
 * 先执行本地硬规则，再把候选集交给模型做细粒度路由；模型失效时仍可回退到确定性规则。
 */
public class HybridSupervisorAgent implements SupervisorAgent {

    private final ChatModel chatModel;
    private final SupervisorContextFactory contextFactory;

    public HybridSupervisorAgent(ChatModel chatModel, SupervisorContextFactory contextFactory) {
        this.chatModel = chatModel;
        this.contextFactory = contextFactory;
    }

    @Override
    public AgentType type() {
        return AgentType.SUPERVISOR;
    }

    @Override
    public SupervisorDecision decide(SupervisorContext context) {
        ReviewerFeedback reviewerFeedback = latestReviewerFeedback(context);
        // reviewer 满足三项硬条件时直接收口，避免再次把已验收的内容送回 writer/researcher 造成抖动。
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
            // 连续两轮没有正文和摘要层面的变化时直接停止，避免 supervisor 在无效循环里持续调度。
            return new SupervisorDecision.Complete(
                    context.getCurrentContent(),
                    summarize(context),
                    "no progress detected"
            );
        }

        // 这里只保留硬规则过滤，避免用不可靠的关键词意图判断提前排除 worker。
        List<SupervisorContext.WorkerDefinition> candidates = selectCandidates(context);
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
                SupervisorContext.WorkerDefinition selectedWorker = candidates.stream()
                        .filter(worker -> worker.getWorkerId().equals(routingResponse.getWorkerId()))
                        .findFirst()
                        .orElse(null);
                if (selectedWorker != null) {
                    return new SupervisorDecision.AssignWorker(
                            selectedWorker.getWorkerId(),
                            firstNonBlank(routingResponse.getInstruction(), contextFactory.buildFallbackInstruction(selectedWorker, context)),
                            firstNonBlank(routingResponse.getReasoning(), "model selected candidate")
                    );
                }
            }
        }

        SupervisorContext.WorkerDefinition fallbackWorker = candidates.get(0);
        return new SupervisorDecision.AssignWorker(
                fallbackWorker.getWorkerId(),
                contextFactory.buildFallbackInstruction(fallbackWorker, context),
                "rule-based fallback"
        );
    }

    private List<SupervisorContext.WorkerDefinition> selectCandidates(SupervisorContext context) {
        String demotedWorkerId = consecutiveWorkerId(context);
        // 连续两轮重复命中同一 worker 时，下一轮先把它降级移出候选，强制 supervisor 尝试别的路径。
        List<SupervisorContext.WorkerDefinition> allowedWorkers = context.getAvailableWorkers().stream()
                .filter(worker -> !worker.getWorkerId().equals(demotedWorkerId))
                .toList();
        if (allowedWorkers.isEmpty()) {
            return List.of();
        }

        ArrayList<SupervisorContext.WorkerDefinition> ordered = new ArrayList<>();
        if (context.getWorkerResults().isEmpty()) {
            addRemaining(ordered, allowedWorkers);
            return List.copyOf(ordered);
        }

        SupervisorContext.WorkerResult latest = context.getWorkerResults().get(context.getWorkerResults().size() - 1);
        if (SupervisorWorkerIds.WRITER.equals(latest.getWorkerId())) {
            // writer 产出后的下一跳默认先进入 reviewer 复查，避免未审查内容直接被判断完成或再次写作。
            addByWorkerId(ordered, allowedWorkers, SupervisorWorkerIds.REVIEWER);
        } else if (SupervisorWorkerIds.REVIEWER.equals(latest.getWorkerId())) {
            ReviewerFeedback reviewerFeedback = parseReviewerFeedback(latest.getSummary());
            if (reviewerFeedback != null) {
                // reviewer 只报告问题类型，不直接命令下一跳；这里按固定 worker 身份重排优先级，
                // 避免再依赖 capability 字符串这类容易漂移的展示字段。
                if (!reviewerFeedback.isEvidenceGrounded()) {
                    addByWorkerId(ordered, allowedWorkers, SupervisorWorkerIds.RESEARCHER);
                    addByWorkerId(ordered, allowedWorkers, SupervisorWorkerIds.WRITER);
                } else if (!reviewerFeedback.isInstructionSatisfied()
                        || !reviewerFeedback.getMissingRequirements().isEmpty()) {
                    addByWorkerId(ordered, allowedWorkers, SupervisorWorkerIds.WRITER);
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

        SupervisorContext.WorkerResult latest = context.getWorkerResults().get(context.getWorkerResults().size() - 1);
        SupervisorContext.WorkerResult previous = context.getWorkerResults().get(context.getWorkerResults().size() - 2);
        // 同时比较“正文内容”和“worker 摘要”，避免仅内容相同但解释变化时被误判为无进展。
        return Objects.equals(latest.getUpdatedContent(), context.getCurrentContent())
                && Objects.equals(previous.getUpdatedContent(), context.getCurrentContent())
                && Objects.equals(latest.getUpdatedContent(), previous.getUpdatedContent())
                && Objects.equals(latest.getSummary(), previous.getSummary());
    }

    private String consecutiveWorkerId(SupervisorContext context) {
        if (context.getWorkerResults().size() < 2) {
            return null;
        }

        SupervisorContext.WorkerResult latest = context.getWorkerResults().get(context.getWorkerResults().size() - 1);
        SupervisorContext.WorkerResult previous = context.getWorkerResults().get(context.getWorkerResults().size() - 2);
        if (latest.getWorkerId().equals(previous.getWorkerId())) {
            // 若重复调用2次, 下一次不再调用该worker
            return latest.getWorkerId();
        }
        return null;
    }

    private SupervisorRoutingResponse requestModelDecision(SupervisorContext context, List<SupervisorContext.WorkerDefinition> candidates) {
        if (chatModel == null) {
            return null;
        }

        try {
            ModelInvocationContext invocationContext = contextFactory.buildRoutingInvocationContext(context, candidates);
            ChatRequest.Builder requestBuilder = ChatRequest.builder()
                    .messages(invocationContext.getMessages())
                    .toolSpecifications(invocationContext.getToolSpecifications());
            if (invocationContext.getResponseFormat() != null) {
                requestBuilder.responseFormat(invocationContext.getResponseFormat());
            }
            ChatResponse response = chatModel.chat(requestBuilder.build());
            ObservedTokenUsageRecorder.record(context, response);
            if (response == null || response.aiMessage() == null || response.aiMessage().text() == null) {
                return null;
            }
            return StructuredOutputParsers.parseJsonWithMarkdownCleanup(
                    response.aiMessage().text(),
                    SupervisorRoutingResponse.class
            );
        } catch (RuntimeException ignored) {
            // 模型路由失败不应打断主流程，调用方会回退到规则路由保证最小可用性。
            return null;
        } catch (Exception ignored) {
            return null;
        }
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
        SupervisorContext.WorkerResult latest = context.getWorkerResults().get(context.getWorkerResults().size() - 1);
        if (!SupervisorWorkerIds.REVIEWER.equals(latest.getWorkerId())) {
            return null;
        }
        return parseReviewerFeedback(latest.getSummary());
    }

    private ReviewerFeedback parseReviewerFeedback(String summary) {
        return StructuredOutputParsers.parseJsonWithMarkdownCleanup(summary, ReviewerFeedback.class);
    }

    private void addByWorkerId(List<SupervisorContext.WorkerDefinition> ordered,
                               List<SupervisorContext.WorkerDefinition> allowedWorkers,
                               String workerId) {
        allowedWorkers.stream()
                .filter(worker -> worker.getWorkerId().equals(workerId))
                .forEach(worker -> addIfAbsent(ordered, worker));
    }

    private void addRemaining(List<SupervisorContext.WorkerDefinition> ordered, List<SupervisorContext.WorkerDefinition> allowedWorkers) {
        allowedWorkers.forEach(worker -> addIfAbsent(ordered, worker));
    }

    private void addIfAbsent(List<SupervisorContext.WorkerDefinition> ordered, SupervisorContext.WorkerDefinition worker) {
        if (!ordered.contains(worker)) {
            ordered.add(worker);
        }
    }
}
