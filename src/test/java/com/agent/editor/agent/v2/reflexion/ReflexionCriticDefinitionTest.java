package com.agent.editor.agent.v2.reflexion;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.runtime.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.state.*;
import com.agent.editor.agent.v2.trace.DefaultTraceCollector;
import com.agent.editor.agent.v2.trace.InMemoryTraceStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflexionCriticDefinitionTest {

    @Test
    void shouldParsePassCritiqueFromModelResponse() {
        ReflexionCriticDefinition definition = new ReflexionCriticDefinition(
                new RecordingChatModel("""
                        {"verdict":"PASS","feedback":"Looks good","reasoning":"All key requirements are satisfied"}
                        """),
                new DefaultTraceCollector(new InMemoryTraceStore())
        );

        Decision decision = definition.decide(context());

        Decision.Complete complete = assertInstanceOf(Decision.Complete.class, decision);
        ReflexionCritique critique = definition.parseCritique(complete.result());
        assertEquals(ReflexionVerdict.PASS, critique.verdict());
        assertEquals("Looks good", critique.feedback());
    }

    @Test
    void shouldParseReviseCritiqueFromModelResponse() {
        ReflexionCriticDefinition definition = new ReflexionCriticDefinition(
                new RecordingChatModel("""
                        {"verdict":"REVISE","feedback":"Tighten the introduction","reasoning":"The opening is too long"}
                        """),
                new DefaultTraceCollector(new InMemoryTraceStore())
        );

        ReflexionCritique critique = definition.parseCritique(assertInstanceOf(
                Decision.Complete.class,
                definition.decide(context())
        ).result());

        assertEquals(ReflexionVerdict.REVISE, critique.verdict());
        assertEquals("Tighten the introduction", critique.feedback());
        assertEquals("The opening is too long", critique.reasoning());
    }

    @Test
    void shouldRejectInvalidCritiquePayload() {
        ReflexionCriticDefinition definition = new ReflexionCriticDefinition(
                new RecordingChatModel("""
                        {"verdict":"MAYBE","feedback":"unclear","reasoning":"invalid verdict"}
                        """),
                new DefaultTraceCollector(new InMemoryTraceStore())
        );

        String rawCritique = assertInstanceOf(Decision.Complete.class, definition.decide(context())).result();

        assertThrows(IllegalArgumentException.class, () -> definition.parseCritique(rawCritique));
    }

    @Test
    void shouldIncludeExecutionMemoryMessagesWhenCallingCriticModel() {
        RecordingChatModel chatModel = new RecordingChatModel("""
                {"verdict":"PASS","feedback":"Looks good","reasoning":"All key requirements are satisfied"}
                """);
        ReflexionCriticDefinition definition = new ReflexionCriticDefinition(
                chatModel,
                new DefaultTraceCollector(new InMemoryTraceStore())
        );

        definition.decide(new AgentRunContext(
                new ExecutionRequest(
                        "task-critic-2",
                        "session-critic-2",
                        AgentType.REFLEXION,
                        new DocumentSnapshot("doc-1", "title", "Draft body"),
                        "Review this draft and decide pass or revise",
                        3
                ),
                1,
                "Draft body",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.AiChatMessage("I'll inspect the evidence first."),
                        new ChatMessage.ToolExecutionResultChatMessage(
                                "tool-call-1",
                                "analyzeDocument",
                                "{}",
                                "analyzeDocument => intro is too long"
                        )
                )),
                ExecutionStage.RUNNING,
                null,
                java.util.List.of()
        ));

        assertTrue(chatModel.lastRequest.messages().stream()
                .map(this::messageText)
                .anyMatch(text -> text.contains("analyzeDocument => intro is too long")));
    }

    @Test
    void shouldSendJsonSchemaResponseFormatToCriticModel() {
        RecordingChatModel chatModel = new RecordingChatModel("""
                {"verdict":"PASS","feedback":"Looks good","reasoning":"All key requirements are satisfied"}
                """);
        ReflexionCriticDefinition definition = new ReflexionCriticDefinition(
                chatModel,
                new DefaultTraceCollector(new InMemoryTraceStore())
        );

        definition.decide(context());

        assertNotNull(chatModel.lastRequest.responseFormat());
        assertEquals(ResponseFormatType.JSON, chatModel.lastRequest.responseFormat().type());
        assertNotNull(chatModel.lastRequest.responseFormat().jsonSchema());
        assertEquals("reflexion_critique", chatModel.lastRequest.responseFormat().jsonSchema().name());
        JsonObjectSchema rootSchema = assertInstanceOf(
                JsonObjectSchema.class,
                chatModel.lastRequest.responseFormat().jsonSchema().rootElement()
        );
        assertEquals(List.of("verdict", "feedback", "reasoning"), rootSchema.required());
        assertTrue(rootSchema.properties().containsKey("verdict"));
        assertTrue(rootSchema.properties().containsKey("feedback"));
        assertTrue(rootSchema.properties().containsKey("reasoning"));
        assertEquals(Boolean.FALSE, rootSchema.additionalProperties());
    }

    private AgentRunContext context() {
        return new AgentRunContext(
                new ExecutionRequest(
                        "task-critic-1",
                        "session-critic-1",
                        AgentType.REFLEXION,
                        new DocumentSnapshot("doc-1", "title", "Draft body"),
                        "Review this draft and decide pass or revise",
                        3
                ),
                0,
                "Draft body",
                new ChatTranscriptMemory(List.of()),
                ExecutionStage.RUNNING,
                null,
                java.util.List.of()
        );
    }

    private String messageText(dev.langchain4j.data.message.ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            return userMessage.singleText();
        }
        if (message instanceof AiMessage aiMessage) {
            return aiMessage.text();
        }
        return message.toString();
    }

    private static final class RecordingChatModel implements ChatModel {

        private final ChatResponse response;
        private ChatRequest lastRequest;

        private RecordingChatModel(String responseText) {
            this.response = ChatResponse.builder()
                    .aiMessage(AiMessage.from(responseText))
                    .build();
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            this.lastRequest = request;
            return response;
        }
    }
}
