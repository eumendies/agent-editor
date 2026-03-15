package com.agent.editor.agent.v2.orchestration;

import com.agent.editor.agent.v2.definition.AgentDefinition;
import com.agent.editor.agent.v2.definition.AgentType;
import com.agent.editor.agent.v2.definition.Decision;
import com.agent.editor.agent.v2.definition.SupervisorAgentDefinition;
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

class SupervisorOrchestratorTest {

    @Test
    void shouldCoordinateHeterogeneousWorkersAndReturnSupervisorSummary() {
        WorkerRegistry workerRegistry = new WorkerRegistry();
        workerRegistry.register(new WorkerDefinition(
                "analyzer",
                "Analyzer",
                "Inspect the document",
                new StubWorkerAgent("analysis complete"),
                List.of("searchContent", "analyzeDocument")
        ));
        workerRegistry.register(new WorkerDefinition(
                "editor",
                "Editor",
                "Apply document edits",
                new StubWorkerAgent("edited content"),
                List.of("editDocument")
        ));

        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        RecordingEventPublisher eventPublisher = new RecordingEventPublisher();
        TraceStore traceStore = new InMemoryTraceStore();
        SupervisorOrchestrator orchestrator = new SupervisorOrchestrator(
                new ScriptedSupervisorAgentDefinition(),
                workerRegistry,
                runtime,
                eventPublisher,
                new DefaultTraceCollector(traceStore)
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-1",
                "session-1",
                AgentType.SUPERVISOR,
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve this document",
                5
        ));

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals("body -> analyzer -> editor", result.finalContent());
        assertEquals(List.of("analyzer", "editor"), runtime.workerIds());
        assertEquals(List.of(
                List.of("searchContent", "analyzeDocument"),
                List.of("editDocument")
        ), runtime.allowedTools());
        assertEquals(EventType.WORKER_SELECTED, eventPublisher.events().get(0).type());
        assertEquals(EventType.SUPERVISOR_COMPLETED, eventPublisher.events().get(eventPublisher.events().size() - 1).type());
        assertTrue(traceStore.getByTaskId("task-1").stream().anyMatch(trace ->
                trace.category() == TraceCategory.ORCHESTRATION_DECISION
                        && "supervisor.worker.assigned".equals(trace.stage())
                        && "analyzer".equals(trace.payload().get("workerId"))
        ));
        assertTrue(traceStore.getByTaskId("task-1").stream().anyMatch(trace ->
                trace.category() == TraceCategory.ORCHESTRATION_DECISION
                        && "supervisor.completed".equals(trace.stage())
                        && "workers done".equals(trace.payload().get("summary"))
        ));
    }

    @Test
    void shouldFinishOneWorkerPassEvenWhenTaskMaxIterationsIsLow() {
        WorkerRegistry workerRegistry = new WorkerRegistry();
        workerRegistry.register(new WorkerDefinition(
                "analyzer",
                "Analyzer",
                "Inspect the document",
                new StubWorkerAgent("analysis complete"),
                List.of("searchContent")
        ));
        workerRegistry.register(new WorkerDefinition(
                "editor",
                "Editor",
                "Apply document edits",
                new StubWorkerAgent("edited content"),
                List.of("editDocument")
        ));

        SupervisorOrchestrator orchestrator = new SupervisorOrchestrator(
                new ScriptedSupervisorAgentDefinition(),
                workerRegistry,
                new RecordingExecutionRuntime(),
                event -> {},
                new DefaultTraceCollector(new InMemoryTraceStore())
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-2",
                "session-2",
                AgentType.SUPERVISOR,
                new DocumentSnapshot("doc-2", "Title", "body"),
                "Improve this document",
                1
        ));

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals("body -> analyzer -> editor", result.finalContent());
    }

    private static final class ScriptedSupervisorAgentDefinition implements SupervisorAgentDefinition {

        @Override
        public SupervisorDecision decide(SupervisorContext context) {
            if (context.workerResults().isEmpty()) {
                return new SupervisorDecision.AssignWorker("analyzer", "Inspect the document", "start with analysis");
            }
            if (context.workerResults().size() == 1) {
                return new SupervisorDecision.AssignWorker("editor", "Apply the recommended edits", "move to editing");
            }
            return new SupervisorDecision.Complete(context.currentContent(), "workers done", "finalized by supervisor");
        }
    }

    private static final class StubWorkerAgent implements AgentDefinition {

        private final String result;

        private StubWorkerAgent(String result) {
            this.result = result;
        }

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public Decision decide(ExecutionContext context) {
            return new Decision.Complete(result, "done");
        }
    }

    private static final class RecordingExecutionRuntime implements ExecutionRuntime {

        private final List<ExecutionRequest> requests = new ArrayList<>();

        @Override
        public ExecutionResult run(AgentDefinition definition, ExecutionRequest request) {
            requests.add(request);
            String marker = request.allowedTools().contains("editDocument") ? "editor" : "analyzer";
            return new ExecutionResult(marker + " result", request.document().content() + " -> " + marker);
        }

        private List<String> workerIds() {
            return requests.stream().map(ExecutionRequest::workerId).toList();
        }

        private List<List<String>> allowedTools() {
            return requests.stream().map(ExecutionRequest::allowedTools).toList();
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
