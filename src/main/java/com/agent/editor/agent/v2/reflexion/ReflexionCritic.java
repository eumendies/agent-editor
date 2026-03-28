package com.agent.editor.agent.v2.reflexion;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.ToolLoopAgent;
import com.agent.editor.agent.v2.core.agent.ToolLoopDecision;
import com.agent.editor.agent.v2.core.exception.NullChatModelException;
import com.agent.editor.agent.v2.core.runtime.AgentRunContext;
import com.agent.editor.agent.v2.mapper.ExecutionMemoryChatMessageMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.ArrayList;
import java.util.List;

public class ReflexionCritic implements ToolLoopAgent {

    private static final JsonSchema REFLEXION_CRITIQUE_SCHEMA = JsonSchema.builder()
            .name("reflexion_critique")
            .rootElement(JsonObjectSchema.builder()
                    .addProperty("verdict", JsonEnumSchema.builder()
                            .description("Final routing decision for the reflexion loop")
                            .enumValues("PASS", "REVISE")
                            .build())
                    .addStringProperty("feedback", "Concise actionable critique for the actor")
                    .addStringProperty("reasoning", "Short explanation for the verdict")
                    .required("verdict", "feedback", "reasoning")
                    .additionalProperties(false)
                    .build())
            .build();

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;

    public ReflexionCritic(ChatModel chatModel) {
        this(chatModel, new ExecutionMemoryChatMessageMapper());
    }

    ReflexionCritic(ChatModel chatModel,
                    ExecutionMemoryChatMessageMapper memoryChatMessageMapper) {
        this.chatModel = chatModel;
        this.memoryChatMessageMapper = memoryChatMessageMapper;
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

        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(buildMessages(context, buildAnalysisSystemPrompt(), buildUserPrompt(context)))
                .toolSpecifications(context.getToolSpecifications())
                .build());

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
        ChatResponse finalizationResponse = chatModel.chat(ChatRequest.builder()
                .messages(buildFinalizationMessages(context, aiMessage.text()))
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(REFLEXION_CRITIQUE_SCHEMA)
                        .build())
                .build());

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

    private String buildAnalysisSystemPrompt() {
        return """
                You are a critic for a document editing reflexion workflow.
                Review the current draft against the instruction.
                Use tools whenever more evidence is needed.
                You may call tools multiple times until you have enough evidence.
                When you are ready to finish, return critique JSON with:
                - verdict: PASS or REVISE
                - feedback: concise actionable feedback
                - reasoning: concise explanation
                """;
    }

    private String buildFinalizationSystemPrompt() {
        return """
                You are a critic for a document editing reflexion workflow.
                Finalize the critique based on the gathered evidence.
                Do not call any tools.
                Return only strict JSON with:
                - verdict: PASS or REVISE
                - feedback: concise actionable feedback
                - reasoning: concise explanation
                """;
    }

    private String buildUserPrompt(AgentRunContext context) {
        return """
                Current document:
                %s

                Instruction:
                %s
                """.formatted(
                context.state().getCurrentContent(),
                context.getRequest().getInstruction()
        );
    }

    private List<ChatMessage> buildMessages(AgentRunContext context, String systemPrompt, String userPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.addAll(memoryChatMessageMapper.toChatMessages(context.state().getMemory()));
        return messages;
    }

    private List<ChatMessage> buildFinalizationMessages(AgentRunContext context, String analysisText) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(buildFinalizationSystemPrompt()));
        messages.addAll(memoryChatMessageMapper.toChatMessages(context.state().getMemory()));
        messages.add(UserMessage.from("""
                Current document:
                %s

                Draft critique analysis:
                %s
                """.formatted(
                context.state().getCurrentContent(),
                analysisText == null ? "" : analysisText
        )));
        return messages;
    }
}
