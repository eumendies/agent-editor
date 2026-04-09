package com.agent.editor.agent.supervisor.worker;

import com.agent.editor.agent.core.agent.AbstractStreamingToolLoopAgent;
import com.agent.editor.agent.core.agent.AgentType;
import com.agent.editor.agent.core.agent.ToolCall;
import com.agent.editor.agent.core.agent.ToolLoopDecision;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.context.ModelInvocationContext;
import com.agent.editor.agent.memory.ObservedTokenUsageRecorder;
import com.agent.editor.agent.model.StreamingLLMInvoker;
import com.agent.editor.agent.util.StructuredOutputParsers;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * supervisor 下游的 memory worker 定义。
 * 它沿用 tool-loop 模式执行 memorySearch / memoryUpsert，并在完成时尽量返回结构化的 MemoryWorkerSummary。
 */
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

    /**
     * 驱动 memory worker 单轮决策。
     * 如果模型先请求工具，就返回 tool calls；如果直接完成，则优先把结果解析成结构化摘要，便于 supervisor 后续折叠进会话记忆。
     *
     * @param context 当前运行上下文
     * @return tool 调用决策或最终完成结果
     */
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

        // memory worker 的完成结果会进入 supervisor memory，因此优先收敛成稳定结构，避免下游继续解析自由文本。
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
