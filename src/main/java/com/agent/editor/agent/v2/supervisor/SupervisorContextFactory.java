package com.agent.editor.agent.v2.supervisor;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.context.AgentContextFactory;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.context.ModelInvocationContext;
import com.agent.editor.agent.v2.core.context.SupervisorContext;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.ExecutionMemory;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.task.TaskRequest;
import dev.langchain4j.data.message.UserMessage;

import java.util.List;

/**
 * supervisor 工作流的上下文装配器。
 * 负责在 supervisor 视角和 worker 视角之间切换上下文形态，并控制跨轮记忆的压缩方式。
 */
public class SupervisorContextFactory implements AgentContextFactory {

    @Override
    public AgentRunContext prepareInitialContext(TaskRequest request) {
        return new AgentRunContext(
                null,
                0,
                request.getDocument().getContent(),
                request.getMemory(),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );
    }

    public SupervisorContext buildSupervisorContext(TaskRequest request,
                                                    AgentRunContext conversationState,
                                                    List<SupervisorContext.WorkerResult> workerResults,
                                                    List<SupervisorContext.WorkerDefinition> availableWorkers) {
        // 每轮都基于最新 conversationState 重建 SupervisorContext，确保路由决策只依赖当前快照而不是旧引用。
        return SupervisorContext.builder()
                .request(new ExecutionRequest(
                        request.getTaskId(),
                        request.getSessionId(),
                        AgentType.SUPERVISOR,
                        new DocumentSnapshot(
                                request.getDocument().getDocumentId(),
                                request.getDocument().getTitle(),
                                conversationState.getCurrentContent()
                        ),
                        request.getInstruction(),
                        request.getMaxIterations()
                ))
                .iteration(conversationState.getIteration())
                .currentContent(conversationState.getCurrentContent())
                .memory(conversationState.getMemory())
                .stage(ExecutionStage.RUNNING)
                .pendingReason(conversationState.getPendingReason())
                .toolSpecifications(List.of())
                .availableWorkers(List.copyOf(availableWorkers))
                .workerResults(List.copyOf(workerResults))
                .build();
    }

    public AgentRunContext buildWorkerExecutionContext(AgentRunContext conversationState, String currentContent) {
        return new AgentRunContext(
                null,
                conversationState.getIteration(),
                currentContent,
                // worker 执行前只保留摘要型记忆，避免工具调用明细污染下一轮 prompt。
                keepSummaryMemory(conversationState.getMemory()),
                ExecutionStage.RUNNING,
                conversationState.getPendingReason(),
                List.of()
        );
    }

    public AgentRunContext summarizeWorkerResult(AgentRunContext conversationState,
                                                 String workerId,
                                                 ExecutionResult<?> result) {
        // supervisor 只需要知道“谁做了什么、产出了什么”，不需要把 worker 的内部推理全文重新塞回记忆。
        return conversationState.appendMemory(new ChatMessage.UserChatMessage("""
                Previous worker result:
                workerId: %s
                summary: %s
                """.formatted(workerId, normalizeWorkerSummary(result))))
                .withCurrentContent(result.getFinalContent())
                .withStage(ExecutionStage.RUNNING);
    }

    @Override
    public ModelInvocationContext buildModelInvocationContext(AgentRunContext context) {
        if (!(context instanceof SupervisorContext supervisorContext)) {
            throw new IllegalArgumentException("SupervisorContextFactory requires SupervisorContext");
        }
        return buildRoutingInvocationContext(supervisorContext, supervisorContext.getAvailableWorkers());
    }

    public ModelInvocationContext buildRoutingInvocationContext(SupervisorContext context,
                                                                List<SupervisorContext.WorkerDefinition> candidates) {
        // 路由 prompt 明确拆成任务、当前正文、候选 worker、历史结果四段，方便模型做局部决策而不是重建全局状态。
        return new ModelInvocationContext(
                List.of(UserMessage.from("""
                        Task: %s
                        Current content:
                        %s

                        Candidate workers:
                        %s

                        Previous worker results:
                        %s
                        """.formatted(
                        context.getRequest().getInstruction(),
                        context.getCurrentContent(),
                        renderCandidates(candidates),
                        renderWorkerResults(context.getWorkerResults())
                ))),
                List.of(),
                null
        );
    }

    public String buildFallbackInstruction(SupervisorContext.WorkerDefinition worker, SupervisorContext context) {
        return worker.getRole() + ": " + worker.getDescription() + "\nTask: " + context.getRequest().getInstruction();
    }

    private ExecutionMemory keepSummaryMemory(ExecutionMemory memory) {
        if (!(memory instanceof ChatTranscriptMemory transcriptMemory)) {
            return memory;
        }
        // supervisor/worker 的跨轮记忆以结果摘要为主，工具调用与结果明细在这里被裁掉，防止上下文指数膨胀。
        return new ChatTranscriptMemory(transcriptMemory.getMessages().stream()
                .filter(message -> !(message instanceof ChatMessage.AiToolCallChatMessage))
                .filter(message -> !(message instanceof ChatMessage.ToolExecutionResultChatMessage))
                .toList());
    }

    public String renderCandidates(List<SupervisorContext.WorkerDefinition> candidates) {
        if (candidates.isEmpty()) {
            return "No candidate workers";
        }
        return candidates.stream()
                .map(worker -> worker.getWorkerId()
                        + " | role=" + worker.getRole()
                        + " | description=" + worker.getDescription()
                        + " | capabilities=" + String.join(", ", worker.getCapabilities()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("No candidate workers");
    }

    public String renderWorkerResults(List<SupervisorContext.WorkerResult> workerResults) {
        if (workerResults.isEmpty()) {
            return "No worker steps executed";
        }
        return workerResults.stream()
                .map(result -> result.getWorkerId() + ": " + result.getSummary())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("No worker steps executed");
    }

    private String normalizeWorkerSummary(ExecutionResult<?> result) {
        if (result.getFinalMessage() != null && !result.getFinalMessage().isBlank()) {
            return result.getFinalMessage();
        }
        if (result.getFinalContent() != null && !result.getFinalContent().isBlank()) {
            return result.getFinalContent();
        }
        return "worker completed";
    }
}
