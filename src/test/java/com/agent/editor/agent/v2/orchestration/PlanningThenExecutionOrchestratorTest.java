package com.agent.editor.agent.v2.orchestration;

import com.agent.editor.agent.v2.definition.AgentDefinition;
import com.agent.editor.agent.v2.definition.AgentType;
import com.agent.editor.agent.v2.definition.Decision;
import com.agent.editor.agent.v2.definition.PlanningAgentDefinition;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import com.agent.editor.agent.v2.runtime.ExecutionContext;
import com.agent.editor.agent.v2.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.runtime.ExecutionResult;
import com.agent.editor.agent.v2.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.state.DocumentSnapshot;
import com.agent.editor.agent.v2.state.TaskStatus;
import com.agent.editor.agent.v2.trace.DefaultTraceCollector;
import com.agent.editor.agent.v2.trace.InMemoryTraceStore;
import com.agent.editor.agent.v2.trace.TraceCategory;
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
                eventPublisher,
                new DefaultTraceCollector(traceStore)
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-1",
                "session-1",
                AgentType.PLANNING,
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve document",
                5
        ));

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals("body -> Add outline -> Refine tone", result.finalContent());
        assertEquals(List.of("Add outline", "Refine tone"), runtime.instructions());
        assertEquals("body", runtime.requests().get(0).document().content());
        assertEquals("body -> Add outline", runtime.requests().get(1).document().content());
        assertEquals(EventType.PLAN_CREATED, eventPublisher.events().get(0).type());
        assertTrue(traceStore.getByTaskId("task-1").stream().anyMatch(trace ->
                trace.category() == TraceCategory.ORCHESTRATION_DECISION
                        && "planning.plan.created".equals(trace.stage())
                        && trace.payload().containsKey("plan")
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
        public Decision decide(ExecutionContext context) {
            return new Decision.Complete(context.request().instruction(), "done");
        }
    }

    private static final class RecordingExecutionRuntime implements ExecutionRuntime {

        private final List<ExecutionRequest> requests = new ArrayList<>();

        @Override
        public ExecutionResult run(AgentDefinition definition, ExecutionRequest request) {
            requests.add(request);
            return new ExecutionResult(request.instruction(), request.document().content() + " -> " + request.instruction());
        }

        private List<String> instructions() {
            return requests.stream().map(ExecutionRequest::instruction).toList();
        }

        private List<ExecutionRequest> requests() {
            return requests;
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
