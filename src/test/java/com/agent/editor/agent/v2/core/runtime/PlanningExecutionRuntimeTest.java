package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.PlanResult;
import com.agent.editor.agent.v2.core.agent.PlanningAgent;
import com.agent.editor.agent.v2.core.exception.InCorrectAgentException;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningExecutionRuntimeTest {

    @Test
    void shouldCompleteWhenAgentReturnsPlanResult() {
        RecordingEventPublisher eventPublisher = new RecordingEventPublisher();
        ExecutionRuntime runtime = new PlanningExecutionRuntime(eventPublisher);
        ExecutionRequest request = new ExecutionRequest(
                "task-1",
                "session-1",
                AgentType.PLANNING,
                new DocumentSnapshot("doc-1", "title", "body"),
                "split the work",
                3
        );

        ExecutionResult<PlanResult> result = runtime.run(new StaticPlanningAgent("outline", "rewrite"), request);

        assertEquals(2, result.getResult().getPlans().size());
        assertEquals("body", result.getFinalContent());
        assertEquals(ExecutionStage.COMPLETED, result.getFinalState().getStage());
        assertEquals("body", result.getFinalState().getCurrentContent());
        ChatTranscriptMemory transcriptMemory = (ChatTranscriptMemory) result.getFinalState().getMemory();
        assertEquals(2, transcriptMemory.getMessages().size());
        assertEquals("split the work", ((ChatMessage.UserChatMessage) transcriptMemory.getMessages().get(0)).getText());
        assertTrue(((ChatMessage.AiChatMessage) transcriptMemory.getMessages().get(1)).getText().contains("2 step"));
        assertEquals(
                List.of(EventType.TASK_STARTED, EventType.PLAN_CREATED, EventType.TASK_COMPLETED),
                eventPublisher.events().stream().map(ExecutionEvent::getType).toList()
        );
    }

    @Test
    void shouldResumeFromProvidedExecutionState() {
        RecordingPlanningAgent agent = new RecordingPlanningAgent();
        ExecutionRuntime runtime = new PlanningExecutionRuntime(event -> {});
        ExecutionRequest request = new ExecutionRequest(
                "task-2",
                "session-2",
                AgentType.PLANNING,
                new DocumentSnapshot("doc-2", "title", "original body"),
                "resume planning",
                3
        );
        AgentRunContext initialState = new AgentRunContext(2, "resumed body");

        ExecutionResult<PlanResult> result = runtime.run(agent, request, initialState);

        assertEquals(2, agent.seenIteration);
        assertEquals("resumed body", agent.seenContent);
        assertEquals("resume planning", agent.seenInstruction);
        assertEquals("resumed body", result.getFinalContent());
        assertEquals(ExecutionStage.COMPLETED, result.getFinalState().getStage());
    }

    @Test
    void shouldPreserveExistingMemoryWhenPlanSummaryIsAppended() {
        ExecutionRuntime runtime = new PlanningExecutionRuntime(event -> {});
        ExecutionRequest request = new ExecutionRequest(
                "task-3",
                "session-3",
                AgentType.PLANNING,
                new DocumentSnapshot("doc-3", "title", "body"),
                "plan this",
                3
        );
        AgentRunContext initialState = new AgentRunContext(
                null,
                0,
                "body",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("previous turn"),
                        new ChatMessage.AiChatMessage("previous answer")
                )),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );

        ExecutionResult<PlanResult> result = runtime.run(new StaticPlanningAgent("step one"), request, initialState);

        ChatTranscriptMemory transcriptMemory = (ChatTranscriptMemory) result.getFinalState().getMemory();
        assertTrue(transcriptMemory.getMessages().stream().anyMatch(message ->
                message instanceof ChatMessage.UserChatMessage userMessage
                        && "previous turn".equals(userMessage.getText())
        ));
        assertTrue(transcriptMemory.getMessages().stream().anyMatch(message ->
                message instanceof ChatMessage.AiChatMessage aiMessage
                        && "previous answer".equals(aiMessage.getText())
        ));
        assertTrue(transcriptMemory.getMessages().stream().anyMatch(message ->
                message instanceof ChatMessage.UserChatMessage userMessage
                        && "plan this".equals(userMessage.getText())
        ));
        assertTrue(transcriptMemory.getMessages().stream().anyMatch(message ->
                message instanceof ChatMessage.AiChatMessage aiMessage
                        && aiMessage.getText().contains("step one")
        ));
    }

    @Test
    void shouldRejectWrongAgentType() {
        ExecutionRuntime runtime = new PlanningExecutionRuntime(event -> {});
        ExecutionRequest request = new ExecutionRequest(
                "task-4",
                "session-4",
                AgentType.PLANNING,
                new DocumentSnapshot("doc-4", "title", "body"),
                "plan this",
                3
        );

        InCorrectAgentException error = assertThrows(InCorrectAgentException.class, () ->
                runtime.run(new WrongAgent(), request)
        );

        assertEquals("PlanningExecutionRuntime require PlanningAgent type", error.getMessage());
    }

    private static final class StaticPlanningAgent implements PlanningAgent {

        private final List<String> instructions;

        private StaticPlanningAgent(String... instructions) {
            this.instructions = List.of(instructions);
        }

        @Override
        public AgentType type() {
            return AgentType.PLANNING;
        }

        @Override
        public PlanResult createPlan(AgentRunContext agentRunContext) {
            return new PlanResult().withInstructions(instructions);
        }
    }

    private static final class RecordingPlanningAgent implements PlanningAgent {

        private int seenIteration;
        private String seenContent;
        private String seenInstruction;

        @Override
        public AgentType type() {
            return AgentType.PLANNING;
        }

        @Override
        public PlanResult createPlan(AgentRunContext agentRunContext) {
            seenIteration = agentRunContext.getIteration();
            seenContent = agentRunContext.getCurrentContent();
            seenInstruction = agentRunContext.getRequest().getInstruction();
            return new PlanResult().withInstructions(List.of("resume step"));
        }
    }

    private static final class WrongAgent implements Agent {

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }
    }

    private static final class RecordingEventPublisher implements EventPublisher {

        private final List<ExecutionEvent> events = new ArrayList<>();

        @Override
        public void publish(ExecutionEvent event) {
            events.add(event);
        }

        private List<ExecutionEvent> events() {
            return events;
        }
    }
}
