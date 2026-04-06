package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.AbstractStreamingToolLoopAgent;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.ToolCall;
import com.agent.editor.agent.v2.core.agent.ToolLoopDecision;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.context.ModelInvocationContext;
import com.agent.editor.agent.v2.memory.ObservedTokenUsageRecorder;
import com.agent.editor.agent.v2.model.StreamingLLMInvoker;
import com.agent.editor.agent.v2.util.StructuredOutputParsers;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

public class MemoryAgent extends AbstractStreamingToolLoopAgent {

    private final MemoryAgentContextFactory contextFactory;

    public static MemoryAgent blocking(ChatModel chatModel,
                                       MemoryAgentContextFactory contextFactory) {
        return new MemoryAgent(chatModel, null, contextFactory);
    }

    public static MemoryAgent streaming(StreamingLLMInvoker streamingLLMInvoker,
                                        MemoryAgentContextFactory contextFactory) {
        return new MemoryAgent(null, streamingLLMInvoker, contextFactory);
    }

    private MemoryAgent(ChatModel chatModel,
                        StreamingLLMInvoker streamingLLMInvoker,
                        MemoryAgentContextFactory contextFactory) {
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

        MemoryWorkerSummary summary = StructuredOutputParsers.parseJsonWithMarkdownCleanup(aiMessage.text(), MemoryWorkerSummary.class);
        if (summary != null) {
            return new ToolLoopDecision.Complete<>(summary, aiMessage.text());
        }
        return new ToolLoopDecision.Complete<>(aiMessage.text(), aiMessage.text());
    }

    private ToolCall toToolCall(ToolExecutionRequest request) {
        return new ToolCall(request.id(), request.name(), request.arguments());
    }
}
