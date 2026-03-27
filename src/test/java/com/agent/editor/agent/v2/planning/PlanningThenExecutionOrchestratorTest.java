package com.agent.editor.agent.v2.planning;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.*;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import com.agent.editor.agent.v2.core.runtime.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.agent.v2.task.TaskResult;
import com.agent.editor.agent.v2.trace.InMemoryTraceStore;
import com.agent.editor.agent.v2.trace.TraceStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningThenExecutionOrchestratorTest {

    @Test
    void shouldExecutePlanStepsSequentially() {
        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        StaticPlanningAgentDefinition planner = new StaticPlanningAgentDefinition(
                new PlanResult(List.of(
                        new PlanStep(1, "Add outline"),
                        new PlanStep(2, "Refine tone")
                ))
        );
        RecordingEventPublisher eventPublisher = new RecordingEventPublisher();
        TraceStore traceStore = new InMemoryTraceStore();
        PlanningThenExecutionOrchestrator orchestrator = new PlanningThenExecutionOrchestrator(
                planner,
                runtime,
                new CompletingExecutionAgent(),
                eventPublisher
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-1",
                "session-1",
                AgentType.PLANNING,
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve document",
                5
        ));

        assertEquals(TaskStatus.COMPLETED, result.getStatus());
        assertEquals("body -> Add outline -> Refine tone", result.getFinalContent());
        assertEquals(List.of("Add outline", "Refine tone"), runtime.instructions());
        assertEquals("body", runtime.requests().get(0).getDocument().getContent());
        assertEquals("body -> Add outline", runtime.requests().get(1).getDocument().getContent());
        assertEquals(EventType.PLAN_CREATED, eventPublisher.events().get(0).getType());
        assertTrue(traceStore.getByTaskId("task-1").isEmpty());
    }

    @Test
    void shouldReuseExecutionStateAcrossPlanSteps() {
        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        StaticPlanningAgentDefinition planner = new StaticPlanningAgentDefinition(
                new PlanResult(List.of(
                        new PlanStep(1, "Add outline"),
                        new PlanStep(2, "Refine tone")
                ))
        );
        PlanningThenExecutionOrchestrator orchestrator = new PlanningThenExecutionOrchestrator(
                planner,
                runtime,
                new CompletingExecutionAgent(),
                event -> {}
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-2",
                "session-2",
                AgentType.PLANNING,
                new DocumentSnapshot("doc-2", "Title", "body"),
                "Improve document",
                5,
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("previous turn")
                ))
        ));

        assertEquals(TaskStatus.COMPLETED, result.getStatus());
        assertEquals(2, runtime.states().size());
        assertEquals("body", runtime.states().get(0).getCurrentContent());
        assertEquals("body -> Add outline", runtime.states().get(1).getCurrentContent());
        ChatTranscriptMemory firstStepMemory = (ChatTranscriptMemory) runtime.states().get(0).getMemory();
        assertTrue(firstStepMemory.getMessages().stream().anyMatch(message -> "previous turn".equals(message.getText())));
        ChatTranscriptMemory secondStepMemory = (ChatTranscriptMemory) runtime.states().get(1).getMemory();
        assertTrue(secondStepMemory.getMessages().stream().anyMatch(message ->
                message instanceof ChatMessage.UserChatMessage userMessage
                        && userMessage.getText().contains("completed Add outline")
        ));
    }

    private static final class StaticPlanningAgentDefinition extends PlanningAgentDefinition {

        private final PlanResult planResult;

        private StaticPlanningAgentDefinition(PlanResult planResult) {
            super(null);
            this.planResult = planResult;
        }

        @Override
        public PlanResult createPlan(DocumentSnapshot document, String instruction) {
            return planResult;
        }
    }

    private static final class CompletingExecutionAgent implements AgentDefinition {

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public Decision decide(AgentRunContext context) {
            return new Decision.Complete(context.getRequest().getInstruction(), "done");
        }
    }

    private static final class RecordingExecutionRuntime implements ExecutionRuntime {

        private final List<ExecutionRequest> requests = new ArrayList<>();
        private final List<AgentRunContext> states = new ArrayList<>();

        @Override
        public ExecutionResult run(AgentDefinition definition, ExecutionRequest request) {
            requests.add(request);
            return new ExecutionResult(request.getInstruction(), request.getDocument().getContent() + " -> " + request.getInstruction());
        }

        @Override
        public ExecutionResult run(AgentDefinition definition, ExecutionRequest request, AgentRunContext initialState) {
            requests.add(request);
            states.add(initialState);
            String updatedContent = initialState.getCurrentContent() + " -> " + request.getInstruction();
            return new ExecutionResult(
                    request.getInstruction(),
                    updatedContent,
                    new AgentRunContext(
                            request,
                            initialState.getIteration() + 1,
                            updatedContent,
                            new ChatTranscriptMemory(List.of(
                                    new ChatMessage.UserChatMessage("completed " + request.getInstruction())
                            )),
                            ExecutionStage.COMPLETED,
                            null,
                            List.of()
                    )
            );
        }

        private List<String> instructions() {
            return requests.stream().map(ExecutionRequest::getInstruction).toList();
        }

        private List<ExecutionRequest> requests() {
            return requests;
        }

        private List<AgentRunContext> states() {
            return states;
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
