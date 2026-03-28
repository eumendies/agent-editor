package com.agent.editor.agent.v2.react;

import com.agent.editor.agent.v2.core.agent.*;
import com.agent.editor.agent.v2.core.context.AgentContextFactory;
import com.agent.editor.agent.v2.core.context.ModelInvocationContext;
import com.agent.editor.agent.v2.core.exception.NullChatModelException;
import com.agent.editor.agent.v2.core.runtime.AgentRunContext;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public class ReactAgent implements ToolLoopAgent {

    private final ChatModel chatModel;
    private final AgentContextFactory contextFactory;

    public ReactAgent(ChatModel chatModel) {
        this(chatModel, new ReactAgentContextFactory());
    }

    public ReactAgent(ChatModel chatModel,
                      AgentContextFactory contextFactory) {
        this.chatModel = chatModel;
        this.contextFactory = contextFactory;
    }

    @Override
    public AgentType type() {
        return AgentType.REACT;
    }

    @Override
    public ToolLoopDecision decide(AgentRunContext context) throws NullChatModelException {
        if (chatModel == null) {
            throw new NullChatModelException("ChatModel of ReactAgent is not provided");
        }

        ModelInvocationContext invocationContext = contextFactory.buildModelInvocationContext(context);
        ChatRequest.Builder requestBuilder = ChatRequest.builder()
                .messages(invocationContext.getMessages())
                .toolSpecifications(invocationContext.getToolSpecifications());
        if (invocationContext.getResponseFormat() != null) {
            requestBuilder.responseFormat(invocationContext.getResponseFormat());
        }
        ChatResponse response = chatModel.chat(requestBuilder.build());

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
