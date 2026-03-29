package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.SupervisorAgent;
import com.agent.editor.agent.v2.core.agent.SupervisorDecision;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.context.SupervisorContext;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SupervisorExecutionRuntimeTest {

    @Test
    void shouldReturnAssignWorkerDecisionThroughExecutionResult() {
        RecordingEventPublisher eventPublisher = new RecordingEventPublisher();
        ExecutionRuntime runtime = new SupervisorExecutionRuntime(eventPublisher);
        ExecutionRequest request = request();

        ExecutionResult<SupervisorDecision> result = runtime.run(
                new AssigningSupervisorAgent(),
                request,
                supervisorContext(request, "body")
        );

        SupervisorDecision.AssignWorker assignWorker =
                assertInstanceOf(SupervisorDecision.AssignWorker.class, result.getResult());
        assertEquals("writer", assignWorker.getWorkerId());
        assertEquals("assign worker: writer", result.getFinalMessage());
        assertEquals("body", result.getFinalContent());
        assertEquals(ExecutionStage.COMPLETED, result.getFinalState().getStage());
        ChatTranscriptMemory memory = (ChatTranscriptMemory) result.getFinalState().getMemory();
        assertEquals("assign worker: writer", ((ChatMessage.AiChatMessage) memory.getMessages().get(0)).getText());
        assertEquals(
                List.of(EventType.TASK_STARTED, EventType.TASK_COMPLETED),
                eventPublisher.events().stream().map(ExecutionEvent::getType).toList()
        );
    }

    @Test
    void shouldReturnCompleteDecisionThroughExecutionResult() {
        ExecutionRuntime runtime = new SupervisorExecutionRuntime(event -> {});
        ExecutionRequest request = request();

        ExecutionResult<SupervisorDecision> result = runtime.run(
                new CompletingSupervisorAgent(),
                request,
                supervisorContext(request, "draft")
        );

        SupervisorDecision.Complete complete =
                assertInstanceOf(SupervisorDecision.Complete.class, result.getResult());
        assertEquals("final body", complete.getFinalContent());
        assertEquals("complete: all done", result.getFinalMessage());
        assertEquals("final body", result.getFinalContent());
        assertEquals(ExecutionStage.COMPLETED, result.getFinalState().getStage());
    }

    @Test
    void shouldRejectWrongAgentType() {
        ExecutionRuntime runtime = new SupervisorExecutionRuntime(event -> {});

        InCorrectAgentException error = assertThrows(InCorrectAgentException.class, () ->
                runtime.run(new WrongAgent(), request())
        );

        assertEquals("SupervisorExecutionRuntime require SupervisorAgent type", error.getMessage());
    }

    @Test
    void shouldRejectNonSupervisorContextInitialState() {
        ExecutionRuntime runtime = new SupervisorExecutionRuntime(event -> {});

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                runtime.run(new AssigningSupervisorAgent(), request(), new AgentRunContext(0, "body"))
        );

        assertEquals("SupervisorExecutionRuntime require SupervisorContext initial state", error.getMessage());
    }

    private static ExecutionRequest request() {
        return new ExecutionRequest(
                "task-1",
                "session-1",
                AgentType.SUPERVISOR,
                new DocumentSnapshot("doc-1", "title", "body"),
                "improve this document",
                3
        );
    }

    private static SupervisorContext supervisorContext(ExecutionRequest request, String currentContent) {
        return SupervisorContext.builder()
                .request(request)
                .iteration(0)
                .currentContent(currentContent)
                .memory(new ChatTranscriptMemory(List.of()))
                .stage(ExecutionStage.RUNNING)
                .pendingReason(null)
                .toolSpecifications(List.of())
                .availableWorkers(List.of())
                .workerResults(List.of())
                .build();
    }

    private static final class AssigningSupervisorAgent implements SupervisorAgent {

        @Override
        public AgentType type() {
            return AgentType.SUPERVISOR;
        }

        @Override
        public SupervisorDecision decide(SupervisorContext context) {
            return new SupervisorDecision.AssignWorker("writer", "write it", "best next step");
        }
    }

    private static final class CompletingSupervisorAgent implements SupervisorAgent {

        @Override
        public AgentType type() {
            return AgentType.SUPERVISOR;
        }

        @Override
        public SupervisorDecision decide(SupervisorContext context) {
            return new SupervisorDecision.Complete("final body", "all done", "finished");
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
