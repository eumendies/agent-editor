package com.agent.editor.agent.v2.supervisor;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.ToolLoopAgent;
import com.agent.editor.agent.v2.core.agent.ToolLoopDecision;
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
                eventPublisher
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-1",
                "session-1",
                AgentType.SUPERVISOR,
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve this document",
                5
        ));

        assertEquals(TaskStatus.COMPLETED, result.getStatus());
        assertEquals("body -> analyzer -> editor", result.getFinalContent());
        assertEquals(List.of("analyzer", "editor"), runtime.workerIds());
        assertEquals(List.of(
                List.of("searchContent", "analyzeDocument"),
                List.of("editDocument")
        ), runtime.allowedTools());
        assertEquals(EventType.WORKER_SELECTED, eventPublisher.events().get(0).getType());
        assertEquals(EventType.SUPERVISOR_COMPLETED, eventPublisher.events().get(eventPublisher.events().size() - 1).getType());
        assertTrue(traceStore.getByTaskId("task-1").isEmpty());
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
                event -> {}
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-2",
                "session-2",
                AgentType.SUPERVISOR,
                new DocumentSnapshot("doc-2", "Title", "body"),
                "Improve this document",
                1
        ));

        assertEquals(TaskStatus.COMPLETED, result.getStatus());
        assertEquals("body -> analyzer -> editor", result.getFinalContent());
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
                event -> {}
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-3",
                "session-3",
                AgentType.SUPERVISOR,
                new DocumentSnapshot("doc-3", "Title", "body"),
                "Inspect until the issue is clear",
                1
        ));

        assertEquals(TaskStatus.COMPLETED, result.getStatus());
        assertEquals("body -> analyzer -> analyzer", result.getFinalContent());
        assertEquals(List.of("analyzer", "analyzer"), runtime.workerIds());
        assertEquals(3, supervisor.contexts().size());
        assertEquals("body", supervisor.contexts().get(0).getCurrentContent());
        assertEquals("body -> analyzer", supervisor.contexts().get(1).getCurrentContent());
        assertEquals(1, supervisor.contexts().get(1).getWorkerResults().size());
        assertEquals("analyzer result", supervisor.contexts().get(1).getWorkerResults().get(0).getSummary());
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
                event -> {}
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-4",
                "session-4",
                AgentType.SUPERVISOR,
                new DocumentSnapshot("doc-4", "Title", "body"),
                "Improve this document",
                5
        ));

        assertEquals(TaskStatus.COMPLETED, result.getStatus());
        assertEquals(2, runtime.states().size());
        assertEquals("body", runtime.states().get(0).getCurrentContent());
        assertEquals("body -> analyzer", runtime.states().get(1).getCurrentContent());
        assertTrue(((ChatTranscriptMemory) runtime.states().get(0).getMemory()).getMessages().isEmpty());
        ChatTranscriptMemory secondWorkerMemory = (ChatTranscriptMemory) runtime.states().get(1).getMemory();
        assertTrue(secondWorkerMemory.getMessages().stream().anyMatch(message ->
                message instanceof ChatMessage.UserChatMessage userMessage
                        && userMessage.getText().contains("analyzer result")
        ));
        assertTrue(secondWorkerMemory.getMessages().stream().noneMatch(ChatMessage.ToolExecutionResultChatMessage.class::isInstance));
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
                event -> {}
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

        ChatTranscriptMemory firstWorkerMemory = (ChatTranscriptMemory) runtime.states().get(0).getMemory();
        assertTrue(firstWorkerMemory.getMessages().stream().anyMatch(message -> "previous turn".equals(message.getText())));
    }

    private static final class ScriptedSupervisorAgentDefinition implements SupervisorAgentDefinition {

        @Override
        public SupervisorDecision decide(SupervisorContext context) {
            if (context.getWorkerResults().isEmpty()) {
                return new SupervisorDecision.AssignWorker("analyzer", "Inspect the document", "start with analysis");
            }
            if (context.getWorkerResults().size() == 1) {
                return new SupervisorDecision.AssignWorker("editor", "Apply the recommended edits", "move to editing");
            }
            return new SupervisorDecision.Complete(context.getCurrentContent(), "workers done", "finalized by supervisor");
        }
    }

    private static final class RepeatingWorkerSupervisorAgentDefinition implements SupervisorAgentDefinition {

        private final List<SupervisorContext> contexts = new ArrayList<>();

        @Override
        public SupervisorDecision decide(SupervisorContext context) {
            contexts.add(context);
            if (context.getWorkerResults().size() < 2) {
                return new SupervisorDecision.AssignWorker("analyzer", "Inspect the document again", "continue analysis");
            }
            return new SupervisorDecision.Complete(context.getCurrentContent(), "repeated worker done", "enough context collected");
        }

        private List<SupervisorContext> contexts() {
            return contexts;
        }
    }

    private static final class StubWorkerAgent implements ToolLoopAgent {

        private final String result;

        private StubWorkerAgent(String result) {
            this.result = result;
        }

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public ToolLoopDecision decide(AgentRunContext context) {
            return new ToolLoopDecision.Complete(result, "done");
        }
    }

    private static final class RecordingExecutionRuntime implements ExecutionRuntime {

        private final List<ExecutionRequest> requests = new ArrayList<>();
        private final List<AgentRunContext> states = new ArrayList<>();

        @Override
        public ExecutionResult run(Agent agent, ExecutionRequest request) {
            requests.add(request);
            String marker = request.getAllowedTools().contains("editDocument") ? "editor" : "analyzer";
            return new ExecutionResult(marker + " result", request.getDocument().getContent() + " -> " + marker);
        }

        @Override
        public ExecutionResult run(Agent definition, ExecutionRequest request, AgentRunContext initialState) {
            requests.add(request);
            states.add(initialState);
            String marker = request.getAllowedTools().contains("editDocument") ? "editor" : "analyzer";
            String updatedContent = initialState.getCurrentContent() + " -> " + marker;
            return new ExecutionResult(
                    marker + " result",
                    marker + " result",
                    updatedContent,
                    new AgentRunContext(
                            request,
                            initialState.getIteration() + 1,
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
            return requests.stream().map(ExecutionRequest::getWorkerId).toList();
        }

        private List<List<String>> allowedTools() {
            return requests.stream().map(ExecutionRequest::getAllowedTools).toList();
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
