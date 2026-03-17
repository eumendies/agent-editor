package com.agent.editor.agent.v2.reflexion;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.runtime.ExecutionContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionState;
import com.agent.editor.agent.v2.trace.DefaultTraceCollector;
import com.agent.editor.agent.v2.trace.InMemoryTraceStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private ExecutionContext context() {
        return new ExecutionContext(
                new ExecutionRequest(
                        "task-critic-1",
                        "session-critic-1",
                        AgentType.REFLEXION,
                        new DocumentSnapshot("doc-1", "title", "Draft body"),
                        "Review this draft and decide pass or revise",
                        3
                ),
                new ExecutionState(0, "Draft body"),
                java.util.List.of()
        );
    }

    private static final class RecordingChatModel implements ChatModel {

        private final ChatResponse response;

        private RecordingChatModel(String responseText) {
            this.response = ChatResponse.builder()
                    .aiMessage(AiMessage.from(responseText))
                    .build();
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            return response;
        }
    }
}
