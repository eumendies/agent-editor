package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.*;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.context.ModelInvocationContext;
import com.agent.editor.agent.v2.memory.ObservedTokenUsageRecorder;
import com.agent.editor.agent.v2.model.StreamingLLMInvoker;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * writer worker。
 * 基于已有上下文和证据修改文档，边界是不生成缺乏证据支撑的新事实。
 */
public class GroundedWriterAgent extends AbstractStreamingToolLoopAgent {

    private final GroundedWriterAgentContextFactory contextFactory;

    public static GroundedWriterAgent blocking(ChatModel chatModel,
                                               GroundedWriterAgentContextFactory contextFactory) {
        return new GroundedWriterAgent(chatModel, null, contextFactory);
    }

    public static GroundedWriterAgent streaming(StreamingLLMInvoker streamingLLMInvoker,
                                                GroundedWriterAgentContextFactory contextFactory) {
        return new GroundedWriterAgent(null, streamingLLMInvoker, contextFactory);
    }

    private GroundedWriterAgent(ChatModel chatModel,
                                StreamingLLMInvoker streamingLLMInvoker,
                                GroundedWriterAgentContextFactory contextFactory) {
        super(chatModel, streamingLLMInvoker);
        this.contextFactory = contextFactory;
    }

    @Override
    public AgentType type() {
        return AgentType.REACT;
    }

    @Override
    public ToolLoopDecision decide(AgentRunContext context) {
        ModelInvocationContext invocationContext = contextFactory.buildModelInvocationContext(context);
        ChatResponse response = invokeModel(context, invocationContext);
        ObservedTokenUsageRecorder.record(context, response);

        AiMessage aiMessage = response.aiMessage();
        if (aiMessage.hasToolExecutionRequests()) {
            return new ToolLoopDecision.ToolCalls(
                    aiMessage.toolExecutionRequests().stream()
                            .map(this::toToolCall)
                            .toList(),
                    aiMessage.text()
            );
        }

        return new ToolLoopDecision.Complete(aiMessage.text(), aiMessage.text());
    }
    private ToolCall toToolCall(ToolExecutionRequest request) {
        return new ToolCall(request.id(), request.name(), request.arguments());
    }
}
