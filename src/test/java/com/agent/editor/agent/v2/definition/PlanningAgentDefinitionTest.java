package com.agent.editor.agent.v2.definition;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.orchestration.PlanResult;
import com.agent.editor.agent.v2.orchestration.PlanStep;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PlanningAgentDefinitionTest {

    @Test
    void shouldReportPlanningType() {
        PlanningAgentDefinition definition = new PlanningAgentDefinition(null);

        assertEquals(AgentType.PLANNING, definition.type());
    }

    @Test
    void shouldParseNumberedPlanFromModelResponse() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("""
                        1. Review structure
                        2. Rewrite introduction
                        3. Polish tone
                        """))
                .build());
        PlanningAgentDefinition definition = new PlanningAgentDefinition(chatModel);

        PlanResult result = definition.createPlan(
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve this document"
        );

        assertEquals(3, result.steps().size());
        assertEquals(new PlanStep(1, "Review structure"), result.steps().get(0));
        assertEquals(new PlanStep(3, "Polish tone"), result.steps().get(2));
        assertInstanceOf(ChatRequest.class, chatModel.lastRequest);
    }

    @Test
    void shouldFallbackToSingleStepPlanWhenModelUnavailable() {
        PlanningAgentDefinition definition = new PlanningAgentDefinition(null);

        PlanResult result = definition.createPlan(
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve this document"
        );

        assertEquals(List.of(new PlanStep(1, "Improve this document")), result.steps());
    }

    private static final class RecordingChatModel implements ChatModel {

        private final ChatResponse response;
        private ChatRequest lastRequest;

        private RecordingChatModel(ChatResponse response) {
            this.response = response;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            this.lastRequest = request;
            return response;
        }
    }
}
