package com.agent.editor.agent.v2.planning;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PlanningAgentTest {

    @Test
    void shouldReportPlanningType() {
        PlanningAgent definition = new PlanningAgent((PlanningAiService) null);

        assertEquals(AgentType.PLANNING, definition.type());
    }

    @Test
    void shouldMapTypedPlanFromAiService() {
        PlanningAgent definition = new PlanningAgent((document, instruction) ->
                new PlanningResponse(List.of(
                        new PlanningResponse.Step("Review structure"),
                        new PlanningResponse.Step("Rewrite introduction"),
                        new PlanningResponse.Step("Polish tone")
                )));

        PlanResult result = definition.createPlan(
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve this document"
        );

        assertEquals(3, result.getSteps().size());
        assertEquals(new PlanStep(1, "Review structure"), result.getSteps().get(0));
        assertEquals(new PlanStep(3, "Polish tone"), result.getSteps().get(2));
    }

    @Test
    void shouldMapStructuredPlanningResponseThroughAiService() {
        RecordingChatModel chatModel = new RecordingChatModel("""
                {"steps":[{"instruction":"Review structure"},{"instruction":"Rewrite introduction"}]}
                """);
        PlanningAiService planningAiService = AiServices.builder(PlanningAiService.class)
                .chatModel(chatModel)
                .build();

        PlanningResponse response = planningAiService.plan("body", "Improve this document");

        assertEquals(2, response.getSteps().size());
        assertEquals("Review structure", response.getSteps().get(0).getInstruction());
        assertInstanceOf(ChatRequest.class, chatModel.lastRequest);
    }

    @Test
    void shouldFallbackToSingleStepPlanWhenAiServiceUnavailable() {
        PlanningAgent definition = new PlanningAgent((PlanningAiService) null);

        PlanResult result = definition.createPlan(
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve this document"
        );

        assertEquals(List.of(new PlanStep(1, "Improve this document")), result.getSteps());
    }

    @Test
    void shouldFallbackToSingleStepPlanWhenAiServiceReturnsNoSteps() {
        PlanningAgent definition = new PlanningAgent((document, instruction) ->
                new PlanningResponse(List.of()));

        PlanResult result = definition.createPlan(
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve this document"
        );

        assertEquals(List.of(new PlanStep(1, "Improve this document")), result.getSteps());
    }

    @Test
    void shouldFallbackToSingleStepPlanWhenAiServiceFails() {
        PlanningAgent definition = new PlanningAgent((document, instruction) -> {
            throw new IllegalStateException("boom");
        });

        PlanResult result = definition.createPlan(
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve this document"
        );

        assertEquals(List.of(new PlanStep(1, "Improve this document")), result.getSteps());
    }

    private static final class RecordingChatModel implements ChatModel {

        private final String response;
        private ChatRequest lastRequest;

        private RecordingChatModel(String response) {
            this.response = response;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            this.lastRequest = request;
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(response))
                    .build();
        }
    }
}
