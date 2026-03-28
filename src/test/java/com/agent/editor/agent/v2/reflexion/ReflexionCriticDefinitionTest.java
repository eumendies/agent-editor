package com.agent.editor.agent.v2.reflexion;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.ToolLoopDecision;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.runtime.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.state.*;
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
        ReflexionCritic definition = new ReflexionCritic(
                new RecordingChatModel("""
                        {"verdict":"PASS","feedback":"Looks good","reasoning":"All key requirements are satisfied"}
                        """)
        );

        ToolLoopDecision toolLoopDecision = definition.decide(context());

        ToolLoopDecision.Complete complete = assertInstanceOf(ToolLoopDecision.Complete.class, toolLoopDecision);
        ReflexionCritique critique = definition.parseCritique((String) complete.getResult());
        assertEquals(ReflexionVerdict.PASS, critique.getVerdict());
        assertEquals("Looks good", critique.getFeedback());
    }

    @Test
    void shouldParseReviseCritiqueFromModelResponse() {
        ReflexionCritic definition = new ReflexionCritic(
                new RecordingChatModel("""
                        {"verdict":"REVISE","feedback":"Tighten the introduction","reasoning":"The opening is too long"}
                        """)
        );

        ReflexionCritique critique = definition.parseCritique(assertInstanceOf(
                ToolLoopDecision.Complete.class,
                definition.decide(context())
        ).getResult().toString());

        assertEquals(ReflexionVerdict.REVISE, critique.getVerdict());
        assertEquals("Tighten the introduction", critique.getFeedback());
        assertEquals("The opening is too long", critique.getReasoning());
    }

    @Test
    void shouldRejectInvalidCritiquePayload() {
        ReflexionCritic definition = new ReflexionCritic(
                new RecordingChatModel("""
                        {"verdict":"MAYBE","feedback":"unclear","reasoning":"invalid verdict"}
                        """)
        );

        String rawCritique = assertInstanceOf(ToolLoopDecision.Complete.class, definition.decide(context())).getResult().toString();

        assertThrows(IllegalArgumentException.class, () -> definition.parseCritique(rawCritique));
    }

    @Test
    void shouldIncludeExecutionMemoryMessagesWhenCallingCriticModel() {
        RecordingChatModel chatModel = new RecordingChatModel("""
                {"verdict":"PASS","feedback":"Looks good","reasoning":"All key requirements are satisfied"}
                """);
        ReflexionCritic definition = new ReflexionCritic(chatModel);

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
        ReflexionCritic definition = new ReflexionCritic(chatModel);

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
