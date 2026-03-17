package com.agent.editor.agent.v2.reflexion;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.runtime.ExecutionContext;
import com.agent.editor.agent.v2.trace.TraceCategory;
import com.agent.editor.agent.v2.trace.TraceCollector;
import com.agent.editor.agent.v2.trace.TraceRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ReflexionCriticDefinition implements AgentDefinition {

    private final ChatModel chatModel;
    private final TraceCollector traceCollector;
    private final ObjectMapper objectMapper;

    public ReflexionCriticDefinition(ChatModel chatModel, TraceCollector traceCollector) {
        this.chatModel = chatModel;
        this.traceCollector = traceCollector;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public AgentType type() {
        return AgentType.REFLEXION;
    }

    @Override
    public Decision decide(ExecutionContext context) {
        if (chatModel == null) {
            return new Decision.Complete("""
                    {"verdict":"REVISE","feedback":"Critic model unavailable","reasoning":"fallback"}
                    """.trim(), "critic stub");
        }

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(context);
        traceCollector.collect(traceRecord(
                context,
                TraceCategory.MODEL_REQUEST,
                "reflexion.critic.model.request",
                Map.of(
                        "systemPrompt", systemPrompt,
                        "userPrompt", userPrompt,
                        "toolSpecifications", context.toolSpecifications().stream().map(spec -> spec.name()).toList()
                )
        ));

        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(java.util.List.of(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userPrompt)
                ))
                .toolSpecifications(context.toolSpecifications())
                .build());

        AiMessage aiMessage = response.aiMessage();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rawText", aiMessage.text());
        payload.put("toolCalls", aiMessage.toolExecutionRequests().stream()
                .map(request -> Map.of("name", request.name(), "arguments", request.arguments()))
                .toList());
        traceCollector.collect(traceRecord(
                context,
                TraceCategory.MODEL_RESPONSE,
                "reflexion.critic.model.response",
                payload
        ));

        if (aiMessage.hasToolExecutionRequests()) {
            return new Decision.ToolCalls(
                    aiMessage.toolExecutionRequests().stream()
                            .map(request -> new com.agent.editor.agent.v2.core.agent.ToolCall(request.name(), request.arguments()))
                            .toList(),
                    aiMessage.text()
            );
        }

        return new Decision.Complete(aiMessage.text(), "critic complete");
    }

    public ReflexionCritique parseCritique(String rawText) {
        try {
            ReflexionCritique critique = objectMapper.readValue(rawText, ReflexionCritique.class);
            if (critique.verdict() == null) {
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
                Return strict JSON with:
                - verdict: PASS or REVISE
                - feedback: concise actionable feedback
                - reasoning: concise explanation
                Use tools when analysis is needed before finalizing your critique.
                """;
    }

    private String buildUserPrompt(ExecutionContext context) {
        return """
                Current document:
                %s

                Instruction:
                %s
                """.formatted(
                context.state().currentContent(),
                context.request().instruction()
        );
    }

    private TraceRecord traceRecord(ExecutionContext context,
                                    TraceCategory category,
                                    String stage,
                                    Map<String, Object> payload) {
        return new TraceRecord(
                UUID.randomUUID().toString(),
                context.request().taskId(),
                Instant.now(),
                category,
                stage,
                type(),
                context.request().workerId(),
                context.state().iteration(),
                payload
        );
    }
}
