package com.agent.editor.agent.v2.core.agent;

import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.context.ModelInvocationContext;
import com.agent.editor.agent.v2.model.StreamingInvocationResult;
import com.agent.editor.agent.v2.model.StreamingLLMInvoker;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public abstract class AbstractStreamingToolLoopAgent implements ToolLoopAgent {

    protected final ChatModel chatModel;
    protected final StreamingLLMInvoker streamingLLMInvoker;

    protected AbstractStreamingToolLoopAgent(ChatModel chatModel,
                                             StreamingLLMInvoker streamingLLMInvoker) {
        // 这里把“至少存在一种模型调用入口”的约束收口到基类，避免子类各自散落空判断并漏掉 NPE 风险。
        if (chatModel == null && streamingLLMInvoker == null) {
            throw new IllegalArgumentException("Either chatModel or streamingLLMInvoker must be provided");
        }
        this.chatModel = chatModel;
        this.streamingLLMInvoker = streamingLLMInvoker;
    }

    protected ChatResponse invokeModel(AgentRunContext context, ModelInvocationContext invocationContext) {
        ChatRequest request = toChatRequest(invocationContext);
        if (streamingLLMInvoker != null) {
            StreamingInvocationResult result = streamingLLMInvoker.invoke(context.getTaskIdOrEmpty(), request);
            return result.getChatResponse();
        }
        return chatModel.chat(request);
    }

    protected ChatRequest toChatRequest(ModelInvocationContext invocationContext) {
        ChatRequest.Builder requestBuilder = ChatRequest.builder()
                .messages(invocationContext.getMessages())
                .toolSpecifications(invocationContext.getToolSpecifications());
        if (invocationContext.getResponseFormat() != null) {
            requestBuilder.responseFormat(invocationContext.getResponseFormat());
        }
        return requestBuilder.build();
    }
}
