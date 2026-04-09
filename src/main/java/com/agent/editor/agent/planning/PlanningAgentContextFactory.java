package com.agent.editor.agent.planning;

import com.agent.editor.agent.core.agent.PlanResult;
import com.agent.editor.agent.core.context.AgentContextFactory;
import com.agent.editor.agent.core.context.ModelInvocationContext;
import com.agent.editor.agent.core.context.CompressContextMemory;
import com.agent.editor.agent.core.context.MemoryCompressionCapableContextFactory;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.core.memory.ExecutionMemory;
import com.agent.editor.agent.core.memory.MemoryCompressor;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.runtime.ExecutionResult;
import com.agent.editor.agent.core.state.ExecutionStage;
import com.agent.editor.agent.mapper.ExecutionMemoryChatMessageMapper;
import com.agent.editor.agent.task.TaskRequest;

import java.util.ArrayList;
import java.util.List;

public class PlanningAgentContextFactory implements AgentContextFactory, MemoryCompressionCapableContextFactory {

    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;
    private final MemoryCompressor memoryCompressor;

    public PlanningAgentContextFactory(MemoryCompressor memoryCompressor) {
        this(new ExecutionMemoryChatMessageMapper(), memoryCompressor);
    }

    public PlanningAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
                                       MemoryCompressor memoryCompressor) {
        this.memoryChatMessageMapper = memoryChatMessageMapper;
        this.memoryCompressor = memoryCompressor;
    }

    @Override
    @CompressContextMemory
    public AgentRunContext prepareInitialContext(TaskRequest request) {
        return new AgentRunContext(
                null,
                0,
                request.getDocument().getContent(),
                appendUserMessage(request.getMemory(), request.getInstruction()),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );
    }

    /**
     * 为计划执行阶段创建初始上下文。
     *
     * @param request 原始任务请求
     * @return 可直接交给执行 agent 的初始上下文
     */
    @CompressContextMemory
    public AgentRunContext prepareExecutionInitialContext(TaskRequest request) {
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

    /**
     * 为单个计划步骤创建执行上下文，并把步骤指令作为新的 user message 追加到记忆末尾。
     *
     * @param currentState 规划执行阶段的当前状态
     * @param step 当前要执行的计划步骤
     * @return 面向该步骤的新上下文
     */
    @CompressContextMemory
    public AgentRunContext prepareExecutionStepContext(AgentRunContext currentState, PlanResult.PlanStep step) {
        return new AgentRunContext(
                currentState.getRequest(),
                currentState.getIteration(),
                currentState.getCurrentContent(),
                appendUserMessage(currentState.getMemory(), formatPlanStep(step)),
                ExecutionStage.RUNNING,
                currentState.getPendingReason(),
                currentState.getToolSpecifications()
        );
    }

    /**
     * 将已完成步骤的结果摘要写回记忆，并更新当前文档内容。
     *
     * @param stepContext 刚执行完该步骤时的上下文
     * @param result 当前步骤的执行结果
     * @return 供下一步骤继续使用的新上下文
     */
    @CompressContextMemory
    public AgentRunContext summarizeCompletedStep(AgentRunContext stepContext, ExecutionResult<?> result) {
        return stepContext
                .withCurrentContent(result.getFinalContent())
                .appendMemory(new ChatMessage.AiChatMessage("Step result: " + normalizeStepResult(result)))
                .withStage(ExecutionStage.RUNNING);
    }

    /**
     * 将 planning 上下文转换成模型调用所需的聊天消息。
     *
     * @param context planning 当前状态
     * @return 模型调用上下文
     */
    @Override
    public ModelInvocationContext buildModelInvocationContext(AgentRunContext context) {
        return new ModelInvocationContext(
                memoryChatMessageMapper.toChatMessages(context.getMemory()),
                context.getToolSpecifications(),
                null
        );
    }

    private ExecutionMemory appendUserMessage(ExecutionMemory memory, String instruction) {
        if (!(memory instanceof ChatTranscriptMemory transcriptMemory)) {
            return memory;
        }
        ArrayList<ChatMessage> messages = new ArrayList<>(transcriptMemory.getMessages());
        messages.add(new ChatMessage.UserChatMessage(instruction));
        return new ChatTranscriptMemory(messages, transcriptMemory.getLastObservedTotalTokens());
    }

    private String formatPlanStep(PlanResult.PlanStep step) {
        return "Plan step %d: %s".formatted(step.getOrder(), step.getInstruction());
    }

    private String normalizeStepResult(ExecutionResult<?> result) {
        if (result.getFinalMessage() != null && !result.getFinalMessage().isBlank()) {
            return result.getFinalMessage();
        }
        if (result.getFinalContent() != null && !result.getFinalContent().isBlank()) {
            return result.getFinalContent();
        }
        return "step completed";
    }

    @Override
    public MemoryCompressor memoryCompressor() {
        return memoryCompressor;
    }
}
