package com.agent.editor.agent.v2.planning;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.PlanResult;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
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

class PlanningAgentImplTest {

    @Test
    void shouldReportPlanningType() {
        PlanningAgentImpl definition = new PlanningAgentImpl((PlanningAiService) null);

        assertEquals(AgentType.PLANNING, definition.type());
    }

    @Test
    void shouldMapTypedPlanFromAiService() {
        PlanningAgentImpl definition = new PlanningAgentImpl((document, instruction) ->
                new PlanningResponse(List.of(
                        new PlanningResponse.Step("Review structure"),
                        new PlanningResponse.Step("Rewrite introduction"),
                        new PlanningResponse.Step("Polish tone")
                )));

        PlanResult result = definition.createPlan(context("body", "Improve this document"));

        assertEquals(3, result.getPlans().size());
        assertEquals(1, result.getPlans().get(0).getOrder());
        assertEquals("Review structure", result.getPlans().get(0).getInstruction());
        assertEquals(3, result.getPlans().get(2).getOrder());
        assertEquals("Polish tone", result.getPlans().get(2).getInstruction());
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
        PlanningAgentImpl definition = new PlanningAgentImpl((PlanningAiService) null);

        PlanResult result = definition.createPlan(context("body", "Improve this document"));

        assertEquals(1, result.getPlans().size());
        assertEquals("Improve this document", result.getPlans().get(0).getInstruction());
    }

    @Test
    void shouldFallbackToSingleStepPlanWhenAiServiceReturnsNoSteps() {
        PlanningAgentImpl definition = new PlanningAgentImpl((document, instruction) ->
                new PlanningResponse(List.of()));

        PlanResult result = definition.createPlan(context("body", "Improve this document"));

        assertEquals(1, result.getPlans().size());
        assertEquals("Improve this document", result.getPlans().get(0).getInstruction());
    }

    @Test
    void shouldFallbackToSingleStepPlanWhenAiServiceFails() {
        PlanningAgentImpl definition = new PlanningAgentImpl((document, instruction) -> {
            throw new IllegalStateException("boom");
        });

        PlanResult result = definition.createPlan(context("body", "Improve this document"));

        assertEquals(1, result.getPlans().size());
        assertEquals("Improve this document", result.getPlans().get(0).getInstruction());
    }

    private static AgentRunContext context(String content, String instruction) {
        return new AgentRunContext(0, content)
                .withRequest(new com.agent.editor.agent.v2.core.runtime.ExecutionRequest(
                        "task-1",
                        "session-1",
                        AgentType.PLANNING,
                        new DocumentSnapshot("doc-1", "Title", content),
                        instruction,
                        3
                ));
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
