package com.agent.editor.agent.v2.reflexion;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
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

public class ReflexionCritic implements Agent {

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
    public Decision decide(AgentRunContext context) {
        if (chatModel == null) {
            // 测试或降级场景下允许 critic 缺席，但仍返回一个结构合法的 revise 结果。
            return new Decision.Complete("""
                    {"verdict":"REVISE","feedback":"Critic model unavailable","reasoning":"fallback"}
                    """.trim(), "critic stub");
        }

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(context);

        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(buildMessages(context, systemPrompt, userPrompt))
                .toolSpecifications(context.getToolSpecifications())
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(REFLEXION_CRITIQUE_SCHEMA)
                        .build())
                .build());

        AiMessage aiMessage = response.aiMessage();
        if (aiMessage.hasToolExecutionRequests()) {
            return new Decision.ToolCalls(
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

        return new Decision.Complete(aiMessage.text(), "critic complete");
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

    private String buildSystemPrompt() {
        return """
                You are a critic for a document editing reflexion workflow.
                Review the current draft against the instruction.
                Return only JSON so the orchestrator can deterministically branch on PASS vs REVISE.
                Return strict JSON with:
                - verdict: PASS or REVISE
                - feedback: concise actionable feedback
                - reasoning: concise explanation
                Use tools when analysis is needed before finalizing your critique.
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
        messages.add(UserMessage.from(userPrompt));
        return messages;
    }
}
