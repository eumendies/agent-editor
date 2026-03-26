package com.agent.editor.agent.v2.supervisor;

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
import com.agent.editor.agent.v2.trace.DefaultTraceCollector;
import com.agent.editor.agent.v2.trace.InMemoryTraceStore;
import com.agent.editor.agent.v2.trace.TraceCategory;
import com.agent.editor.agent.v2.trace.TraceStore;
import com.agent.editor.agent.v2.supervisor.worker.WorkerDefinition;
import com.agent.editor.agent.v2.supervisor.worker.WorkerRegistry;
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

    @Test
    void shouldAllowRepeatedWorkerAssignmentsAndFeedBackResultsToLaterSupervisorTurns() {
        WorkerRegistry workerRegistry = new WorkerRegistry();
        workerRegistry.register(new WorkerDefinition(
                "analyzer",
                "Analyzer",
                "Inspect the document",
                new StubWorkerAgent("analysis complete"),
                List.of("searchContent")
        ));

        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        RepeatingWorkerSupervisorAgentDefinition supervisor = new RepeatingWorkerSupervisorAgentDefinition();
        SupervisorOrchestrator orchestrator = new SupervisorOrchestrator(
                supervisor,
                workerRegistry,
                runtime,
                event -> {},
                new DefaultTraceCollector(new InMemoryTraceStore())
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-3",
                "session-3",
                AgentType.SUPERVISOR,
                new DocumentSnapshot("doc-3", "Title", "body"),
                "Inspect until the issue is clear",
                1
        ));

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals("body -> analyzer -> analyzer", result.finalContent());
        assertEquals(List.of("analyzer", "analyzer"), runtime.workerIds());
        assertEquals(3, supervisor.contexts().size());
        assertEquals("body", supervisor.contexts().get(0).currentContent());
        assertEquals("body -> analyzer", supervisor.contexts().get(1).currentContent());
        assertEquals(1, supervisor.contexts().get(1).workerResults().size());
        assertEquals("analyzer result", supervisor.contexts().get(1).workerResults().get(0).summary());
    }

    @Test
    void shouldIsolateWorkerRunMemoryWhilePassingStructuredWorkerResults() {
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

        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        SupervisorOrchestrator orchestrator = new SupervisorOrchestrator(
                new ScriptedSupervisorAgentDefinition(),
                workerRegistry,
                runtime,
                event -> {},
                new DefaultTraceCollector(new InMemoryTraceStore())
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-4",
                "session-4",
                AgentType.SUPERVISOR,
                new DocumentSnapshot("doc-4", "Title", "body"),
                "Improve this document",
                5
        ));

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals(2, runtime.states().size());
        assertEquals("body", runtime.states().get(0).currentContent());
        assertEquals("body -> analyzer", runtime.states().get(1).currentContent());
        assertTrue(((ChatTranscriptMemory) runtime.states().get(0).memory()).messages().isEmpty());
        ChatTranscriptMemory secondWorkerMemory = (ChatTranscriptMemory) runtime.states().get(1).memory();
        assertTrue(secondWorkerMemory.messages().stream().anyMatch(message ->
                message instanceof ChatMessage.UserChatMessage userMessage
                        && userMessage.text().contains("analyzer result")
        ));
        assertTrue(secondWorkerMemory.messages().stream().noneMatch(ChatMessage.ToolExecutionResultChatMessage.class::isInstance));
    }

    @Test
    void shouldSeedFirstWorkerStateWithSessionMemory() {
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

        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        SupervisorOrchestrator orchestrator = new SupervisorOrchestrator(
                new ScriptedSupervisorAgentDefinition(),
                workerRegistry,
                runtime,
                event -> {},
                new DefaultTraceCollector(new InMemoryTraceStore())
        );

        orchestrator.execute(new TaskRequest(
                "task-5",
                "session-5",
                AgentType.SUPERVISOR,
                new DocumentSnapshot("doc-5", "Title", "body"),
                "Improve this document",
                5,
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("previous turn")
                ))
        ));

        ChatTranscriptMemory firstWorkerMemory = (ChatTranscriptMemory) runtime.states().get(0).memory();
        assertTrue(firstWorkerMemory.messages().stream().anyMatch(message -> "previous turn".equals(message.text())));
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

    private static final class RepeatingWorkerSupervisorAgentDefinition implements SupervisorAgentDefinition {

        private final List<SupervisorContext> contexts = new ArrayList<>();

        @Override
        public SupervisorDecision decide(SupervisorContext context) {
            contexts.add(context);
            if (context.workerResults().size() < 2) {
                return new SupervisorDecision.AssignWorker("analyzer", "Inspect the document again", "continue analysis");
            }
            return new SupervisorDecision.Complete(context.currentContent(), "repeated worker done", "enough context collected");
        }

        private List<SupervisorContext> contexts() {
            return contexts;
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
        public Decision decide(AgentRunContext context) {
            return new Decision.Complete(result, "done");
        }
    }

    private static final class RecordingExecutionRuntime implements ExecutionRuntime {

        private final List<ExecutionRequest> requests = new ArrayList<>();
        private final List<AgentRunContext> states = new ArrayList<>();

        @Override
        public ExecutionResult run(AgentDefinition definition, ExecutionRequest request) {
            requests.add(request);
            String marker = request.allowedTools().contains("editDocument") ? "editor" : "analyzer";
            return new ExecutionResult(marker + " result", request.document().content() + " -> " + marker);
        }

        @Override
        public ExecutionResult run(AgentDefinition definition, ExecutionRequest request, AgentRunContext initialState) {
            requests.add(request);
            states.add(initialState);
            String marker = request.allowedTools().contains("editDocument") ? "editor" : "analyzer";
            String updatedContent = initialState.currentContent() + " -> " + marker;
            return new ExecutionResult(
                    marker + " result",
                    updatedContent,
                    new AgentRunContext(
                            request,
                            initialState.iteration() + 1,
                            updatedContent,
                            new ChatTranscriptMemory(List.of(
                                    new ChatMessage.ToolExecutionResultChatMessage(
                                            marker + "-tool-call",
                                            "workerTool",
                                            null,
                                            marker + " finished"
                                    )
                            )),
                            ExecutionStage.COMPLETED,
                            null,
                            List.of()
                    )
            );
        }

        private List<String> workerIds() {
            return requests.stream().map(ExecutionRequest::workerId).toList();
        }

        private List<List<String>> allowedTools() {
            return requests.stream().map(ExecutionRequest::allowedTools).toList();
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
