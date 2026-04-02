package com.agent.editor.agent.v2.react;

import com.agent.editor.agent.v2.model.StreamingLLMInvoker;
import com.agent.editor.agent.v2.core.agent.*;
import com.agent.editor.agent.v2.core.context.AgentContextFactory;
import com.agent.editor.agent.v2.core.context.ModelInvocationContext;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.memory.ObservedTokenUsageRecorder;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

public class ReactAgent extends AbstractStreamingToolLoopAgent {

    private final AgentContextFactory contextFactory;

    public static ReactAgent blocking(ChatModel chatModel,
                                      AgentContextFactory contextFactory) {
        return new ReactAgent(chatModel, null, contextFactory);
    }

    public static ReactAgent streaming(StreamingLLMInvoker streamingLLMInvoker,
                                       AgentContextFactory contextFactory) {
        return new ReactAgent(null, streamingLLMInvoker, contextFactory);
    }

    protected ReactAgent(ChatModel chatModel,
                         StreamingLLMInvoker streamingLLMInvoker,
                         AgentContextFactory contextFactory) {
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
            // 这里不直接执行工具，只把调用意图翻译成统一 Decision，由 runtime 接管后续步骤。
            return new ToolLoopDecision.ToolCalls(
                    aiMessage.toolExecutionRequests().stream()
                            .map(this::toToolCall)
                            .toList(),
                    aiMessage.text()
            );
        }

        return new ToolLoopDecision.Complete<String>(aiMessage.text(), aiMessage.text());
    }
    private ToolCall toToolCall(ToolExecutionRequest request) {
        return new ToolCall(request.id(), request.name(), request.arguments());
    }
}
