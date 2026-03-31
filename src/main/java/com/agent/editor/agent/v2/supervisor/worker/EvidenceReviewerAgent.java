package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.*;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.context.ModelInvocationContext;
import com.agent.editor.agent.v2.memory.ObservedTokenUsageRecorder;
import com.agent.editor.agent.v2.util.StructuredOutputParsers;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * reviewer worker。
 * 负责把“是否完成指令”和“是否证据扎实”拆成结构化反馈，供 supervisor 继续路由。
 */
public class EvidenceReviewerAgent implements ToolLoopAgent {

    private final ChatModel chatModel;
    private final EvidenceReviewerAgentContextFactory contextFactory;

    public EvidenceReviewerAgent(ChatModel chatModel,
                                 EvidenceReviewerAgentContextFactory contextFactory) {
        this.chatModel = chatModel;
        this.contextFactory = contextFactory;
    }

    @Override
    public AgentType type() {
        return AgentType.REACT;
    }

    @Override
    public ToolLoopDecision decide(AgentRunContext context) {
        if (chatModel == null) {
            return new ToolLoopDecision.Complete("{}", "reviewer stub");
        }

        ModelInvocationContext invocationContext = contextFactory.buildModelInvocationContext(context);
        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(invocationContext.getMessages())
                .toolSpecifications(invocationContext.getToolSpecifications())
                .build());
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

        ReviewerFeedback reviewerFeedback = parseReviewerFeedback(aiMessage.text());
        if (reviewerFeedback != null) {
            return new ToolLoopDecision.Complete<>(reviewerFeedback, aiMessage.text());
        }
        return new ToolLoopDecision.Complete<>(aiMessage.text(), aiMessage.text());
    }

    private ToolCall toToolCall(ToolExecutionRequest request) {
        return new ToolCall(request.id(), request.name(), request.arguments());
    }

    private ReviewerFeedback parseReviewerFeedback(String text) {
        return StructuredOutputParsers.parseJsonWithMarkdownCleanup(text, ReviewerFeedback.class);
    }
}
