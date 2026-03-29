package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.*;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.mapper.ExecutionMemoryChatMessageMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * reviewer worker。
 * 负责把“是否完成指令”和“是否证据扎实”拆成结构化反馈，供 supervisor 继续路由。
 */
public class EvidenceReviewerAgent implements ToolLoopAgent {

    private final ChatModel chatModel;
    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;

    public EvidenceReviewerAgent(ChatModel chatModel) {
        this(chatModel, new ExecutionMemoryChatMessageMapper());
    }

    EvidenceReviewerAgent(ChatModel chatModel,
                          ExecutionMemoryChatMessageMapper memoryChatMessageMapper) {
        this.chatModel = chatModel;
        this.memoryChatMessageMapper = memoryChatMessageMapper;
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

        String systemPrompt = buildSystemPrompt();

        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(buildMessages(context, systemPrompt))
                .toolSpecifications(context.getToolSpecifications())
                .build());

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

    private String buildSystemPrompt() {
        return """
                You are a reviewer worker in a hybrid supervisor workflow.
                Review whether the latest answer follows the user instruction and stays grounded in the available evidence.
                If you need more local inspection, use the available analysis tools before finalizing your review.
                Finish by returning strict JSON matching the ReviewerFeedback shape.
                ReviewerFeedback must explicitly report verdict, instructionSatisfied, evidenceGrounded,
                unsupportedClaims, missingRequirements, feedback, and reasoning.
                """;
    }

    private List<ChatMessage> buildMessages(AgentRunContext context, String systemPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        // reviewer 主要依据累积记忆审查当前结果，因此不再单独注入新的 user message，避免审查目标漂移。
        messages.addAll(memoryChatMessageMapper.toChatMessages(context.state().getMemory()));
        return messages;
    }

    private ToolCall toToolCall(ToolExecutionRequest request) {
        return new ToolCall(request.id(), request.name(), request.arguments());
    }
}
