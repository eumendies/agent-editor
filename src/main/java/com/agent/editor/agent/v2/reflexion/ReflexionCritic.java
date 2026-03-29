package com.agent.editor.agent.v2.reflexion;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.context.ModelInvocationContext;
import com.agent.editor.agent.v2.core.agent.ToolLoopAgent;
import com.agent.editor.agent.v2.core.agent.ToolLoopDecision;
import com.agent.editor.agent.v2.core.exception.NullChatModelException;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public class ReflexionCritic implements ToolLoopAgent {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ReflexionCriticContextFactory contextFactory;

    public ReflexionCritic(ChatModel chatModel) {
        this(chatModel, new ReflexionCriticContextFactory());
    }

    public ReflexionCritic(ChatModel chatModel,
                           ReflexionCriticContextFactory contextFactory) {
        this.chatModel = chatModel;
        this.contextFactory = contextFactory;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public AgentType type() {
        return AgentType.REFLEXION;
    }

    @Override
    public ToolLoopDecision decide(AgentRunContext context) {
        if (chatModel == null) {
            throw new NullChatModelException("Reflexion Critic require non-null ChatModel");
        }

        ChatResponse response = chatModel.chat(toChatRequest(contextFactory.buildModelInvocationContext(context)));

        AiMessage aiMessage = response.aiMessage();
        if (aiMessage.hasToolExecutionRequests()) {
            return new ToolLoopDecision.ToolCalls(
                    aiMessage.toolExecutionRequests().stream()
                            .map(request -> new com.agent.editor.agent.v2.core.agent.ToolCall(
                                    request.id(),
                                    request.name(),
                                    request.arguments()
                            ))
                            .toList(),
                    aiMessage.text()
            );
        }

        ReflexionCritique critique = tryParseCritique(aiMessage.text());
        if (critique != null) {
            return new ToolLoopDecision.Complete<>(critique, "critic complete");
        }

        // 先让模型自由分析和调工具；只有文本结果无法解析成 verdict 时，再补一次严格 JSON 收口。
        ChatResponse finalizationResponse = chatModel.chat(
                toChatRequest(contextFactory.buildFinalizationInvocationContext(context, aiMessage.text()))
        );

        ReflexionCritique finalizedCritique = tryParseCritique(finalizationResponse.aiMessage().text());
        if (finalizedCritique == null) {
            finalizedCritique = new ReflexionCritique(
                    ReflexionVerdict.PASS,
                    "Critic Agent Not Available Now",
                    "Critic Agent Not Available Now"
            );
        }
        return new ToolLoopDecision.Complete<>(finalizedCritique, "critic complete");
    }

    public ReflexionCritique parseCritique(String rawText) {
        try {
            // critic 与 orchestrator 之间只通过结构化 verdict 交互，避免靠自然语言猜测流程分支。
            ReflexionCritique critique = objectMapper.readValue(rawText, ReflexionCritique.class);
            if (critique.getVerdict() == null) {
                throw new IllegalArgumentException("Critique verdict is required");
            }
            return critique;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid reflexion critique payload", exception);
        }
    }

    private ReflexionCritique tryParseCritique(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawText, ReflexionCritique.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private ChatRequest toChatRequest(ModelInvocationContext invocationContext) {
        ChatRequest.Builder requestBuilder = ChatRequest.builder()
                .messages(invocationContext.getMessages())
                .toolSpecifications(invocationContext.getToolSpecifications());
        if (invocationContext.getResponseFormat() != null) {
            requestBuilder.responseFormat(invocationContext.getResponseFormat());
        }
        return requestBuilder.build();
    }
}
