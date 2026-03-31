package com.agent.editor.agent.v2.planning;

import com.agent.editor.agent.v2.core.agent.PlanResult;
import com.agent.editor.agent.v2.core.context.AgentContextFactory;
import com.agent.editor.agent.v2.core.context.ModelInvocationContext;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.ExecutionMemory;
import com.agent.editor.agent.v2.core.memory.MemoryCompressor;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.mapper.ExecutionMemoryChatMessageMapper;
import com.agent.editor.agent.v2.task.TaskRequest;

import java.util.ArrayList;
import java.util.List;

public class PlanningAgentContextFactory implements AgentContextFactory {

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
    public AgentRunContext prepareInitialContext(TaskRequest request) {
        return compressContextMemory(new AgentRunContext(
                null,
                0,
                request.getDocument().getContent(),
                appendUserMessage(request.getMemory(), request.getInstruction()),
                ExecutionStage.RUNNING,
                null,
                List.of()
        ));
    }

    /**
     * 创建第一个Executor Agent的Context
     * @param request
     * @return
     */
    public AgentRunContext prepareExecutionInitialContext(TaskRequest request) {
        return compressContextMemory(new AgentRunContext(
                null,
                0,
                request.getDocument().getContent(),
                request.getMemory(),
                ExecutionStage.RUNNING,
                null,
                List.of()
        ));
    }

    /**
     * 创建每一步Executor Agent的Context
     * @param currentState
     * @param step
     * @return
     */
    public AgentRunContext prepareExecutionStepContext(AgentRunContext currentState, PlanResult.PlanStep step) {
        return compressContextMemory(new AgentRunContext(
                currentState.getRequest(),
                currentState.getIteration(),
                currentState.getCurrentContent(),
                appendUserMessage(currentState.getMemory(), formatPlanStep(step)),
                ExecutionStage.RUNNING,
                currentState.getPendingReason(),
                currentState.getToolSpecifications()
        ));
    }

    /**
     * 总结前一个Plan的完成情况并添加到memory中
     * @param stepContext
     * @param result
     * @return
     */
    public AgentRunContext summarizeCompletedStep(AgentRunContext stepContext, ExecutionResult<?> result) {
        return compressContextMemory(stepContext
                .withCurrentContent(result.getFinalContent())
                .appendMemory(new ChatMessage.AiChatMessage("Step result: " + normalizeStepResult(result)))
                .withStage(ExecutionStage.RUNNING));
    }

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

    private AgentRunContext compressContextMemory(AgentRunContext context) {
        return context.withMemory(memoryCompressor.compressOrOriginal(context.getMemory()));
    }
}
