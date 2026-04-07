package com.agent.editor.agent.v2.supervisor;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.SupervisorAgent;
import com.agent.editor.agent.v2.core.agent.SupervisorDecision;
import com.agent.editor.agent.v2.core.agent.ToolLoopAgent;
import com.agent.editor.agent.v2.core.agent.ToolLoopDecision;
import com.agent.editor.agent.v2.core.context.SupervisorContext;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.*;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.runtime.SupervisorExecutionRuntime;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.agent.v2.task.TaskResult;
import com.agent.editor.agent.v2.trace.InMemoryTraceStore;
import com.agent.editor.agent.v2.trace.TraceStore;
import com.agent.editor.agent.v2.supervisor.worker.WorkerRegistry;
import com.agent.editor.agent.v2.support.NoOpMemoryCompressors;
import com.agent.editor.agent.v2.tool.ExecutionToolAccessPolicy;
import com.agent.editor.agent.v2.tool.ExecutionToolAccessRole;
import com.agent.editor.agent.v2.tool.document.DocumentToolAccessPolicy;
import com.agent.editor.agent.v2.tool.document.DocumentToolNames;
import com.agent.editor.agent.v2.tool.memory.MemoryToolAccessPolicy;
import com.agent.editor.agent.v2.tool.memory.MemoryToolNames;
import com.agent.editor.config.DocumentToolModeProperties;
import com.agent.editor.service.StructuredDocumentService;
import com.agent.editor.utils.rag.markdown.MarkdownSectionTreeBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisorOrchestratorTest {

    @Test
    void shouldCoordinateHeterogeneousWorkersAndReturnSupervisorSummary() {
        WorkerRegistry workerRegistry = new WorkerRegistry();
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                "analyzer",
                "Analyzer",
                "Inspect the document",
                new StubWorkerAgent("analysis complete"),
                List.of("searchContent", "analyzeDocument")
        ));
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                "editor",
                "Editor",
                "Apply document edits",
                new StubWorkerAgent("edited content"),
                List.of("editDocument", "appendToDocument", "getDocumentSnapshot")
        ));

        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        RecordingEventPublisher eventPublisher = new RecordingEventPublisher();
        TraceStore traceStore = new InMemoryTraceStore();
        RecordingSupervisorExecutionRuntime supervisorRuntime = new RecordingSupervisorExecutionRuntime();
        SupervisorOrchestrator orchestrator = new SupervisorOrchestrator(
                new ScriptedSupervisorAgentDefinition(),
                supervisorRuntime,
                workerRegistry,
                runtime,
                eventPublisher,
                new SupervisorContextFactory(NoOpMemoryCompressors.noop()),
                documentToolAccessPolicy(100)
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
        assertEquals(3, supervisorRuntime.runCount);
        assertEquals(List.of(
                List.of("searchContent", "analyzeDocument"),
                List.of("editDocument", "appendToDocument", "getDocumentSnapshot")
        ), runtime.allowedTools());
        assertEquals(EventType.WORKER_SELECTED, eventPublisher.events().get(0).getType());
        assertEquals(EventType.SUPERVISOR_COMPLETED, eventPublisher.events().get(eventPublisher.events().size() - 1).getType());
        assertTrue(traceStore.getByTaskId("task-1").isEmpty());
    }

    @Test
    void shouldFinishOneWorkerPassEvenWhenTaskMaxIterationsIsLow() {
        WorkerRegistry workerRegistry = new WorkerRegistry();
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                "analyzer",
                "Analyzer",
                "Inspect the document",
                new StubWorkerAgent("analysis complete"),
                List.of("searchContent")
        ));
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                "editor",
                "Editor",
                "Apply document edits",
                new StubWorkerAgent("edited content"),
                List.of("editDocument", "appendToDocument", "getDocumentSnapshot")
        ));

        RecordingSupervisorExecutionRuntime supervisorRuntime = new RecordingSupervisorExecutionRuntime();
        SupervisorOrchestrator orchestrator = new SupervisorOrchestrator(
                new ScriptedSupervisorAgentDefinition(),
                supervisorRuntime,
                workerRegistry,
                new RecordingExecutionRuntime(),
                event -> {},
                new SupervisorContextFactory(NoOpMemoryCompressors.noop()),
                documentToolAccessPolicy(100)
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
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                "analyzer",
                "Analyzer",
                "Inspect the document",
                new StubWorkerAgent("analysis complete"),
                List.of("searchContent")
        ));

        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        RepeatingWorkerSupervisorAgentDefinition supervisor = new RepeatingWorkerSupervisorAgentDefinition();
        RecordingSupervisorContextFactory contextFactory = new RecordingSupervisorContextFactory();
        RecordingSupervisorExecutionRuntime supervisorRuntime = new RecordingSupervisorExecutionRuntime();
        SupervisorOrchestrator orchestrator = new SupervisorOrchestrator(
                supervisor,
                supervisorRuntime,
                workerRegistry,
                runtime,
                event -> {},
                contextFactory,
                documentToolAccessPolicy(100)
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
        assertEquals(3, supervisorRuntime.runCount);
        assertEquals(3, contextFactory.supervisorContextBuildCount);
        assertEquals(2, contextFactory.workerExecutionContextBuildCount);
        assertEquals(2, contextFactory.workerSummaryCount);
        assertEquals("body", supervisor.contexts().get(0).getCurrentContent());
        assertEquals("body -> analyzer", supervisor.contexts().get(1).getCurrentContent());
        assertEquals(1, supervisor.contexts().get(1).getWorkerResults().size());
        assertEquals("analyzer result", supervisor.contexts().get(1).getWorkerResults().get(0).getSummary());
    }

    @Test
    void shouldIsolateWorkerRunMemoryWhilePassingStructuredWorkerResults() {
        WorkerRegistry workerRegistry = new WorkerRegistry();
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                "analyzer",
                "Analyzer",
                "Inspect the document",
                new StubWorkerAgent("analysis complete"),
                List.of("searchContent")
        ));
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                "editor",
                "Editor",
                "Apply document edits",
                new StubWorkerAgent("edited content"),
                List.of("editDocument", "appendToDocument", "getDocumentSnapshot")
        ));

        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        SupervisorOrchestrator orchestrator = new SupervisorOrchestrator(
                new ScriptedSupervisorAgentDefinition(),
                new RecordingSupervisorExecutionRuntime(),
                workerRegistry,
                runtime,
                event -> {},
                new SupervisorContextFactory(NoOpMemoryCompressors.noop()),
                documentToolAccessPolicy(100)
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
        ChatTranscriptMemory firstWorkerMemory = (ChatTranscriptMemory) runtime.states().get(0).getMemory();
        assertEquals(2, firstWorkerMemory.getMessages().size());
        assertEquals("Improve this document", firstWorkerMemory.getMessages().get(0).getText());
        assertEquals("Inspect the document", firstWorkerMemory.getMessages().get(1).getText());
        ChatTranscriptMemory secondWorkerMemory = (ChatTranscriptMemory) runtime.states().get(1).getMemory();
        assertTrue(secondWorkerMemory.getMessages().stream().anyMatch(message ->
                message.getText().contains("analyzer result")
        ));
        assertTrue(secondWorkerMemory.getMessages().stream().anyMatch(message ->
                "Apply the recommended edits".equals(message.getText())
        ));
        assertTrue(secondWorkerMemory.getMessages().stream().noneMatch(ChatMessage.ToolExecutionResultChatMessage.class::isInstance));
    }

    @Test
    void shouldSeedFirstWorkerStateWithSessionMemory() {
        WorkerRegistry workerRegistry = new WorkerRegistry();
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                "analyzer",
                "Analyzer",
                "Inspect the document",
                new StubWorkerAgent("analysis complete"),
                List.of("searchContent")
        ));
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                "editor",
                "Editor",
                "Apply document edits",
                new StubWorkerAgent("edited content"),
                List.of("editDocument", "appendToDocument", "getDocumentSnapshot")
        ));

        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        SupervisorOrchestrator orchestrator = new SupervisorOrchestrator(
                new ScriptedSupervisorAgentDefinition(),
                new RecordingSupervisorExecutionRuntime(),
                workerRegistry,
                runtime,
                event -> {},
                new SupervisorContextFactory(NoOpMemoryCompressors.noop()),
                documentToolAccessPolicy(100)
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
        assertTrue(firstWorkerMemory.getMessages().stream().anyMatch(message -> "Improve this document".equals(message.getText())));
    }

    @Test
    void shouldRetainCurrentUserInstructionInReturnedSessionMemory() {
        WorkerRegistry workerRegistry = new WorkerRegistry();
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                "analyzer",
                "Analyzer",
                "Inspect the document",
                new StubWorkerAgent("analysis complete"),
                List.of("searchContent")
        ));
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                "editor",
                "Editor",
                "Apply document edits",
                new StubWorkerAgent("edited content"),
                List.of("editDocument")
        ));

        SupervisorOrchestrator orchestrator = new SupervisorOrchestrator(
                new ScriptedSupervisorAgentDefinition(),
                new RecordingSupervisorExecutionRuntime(),
                workerRegistry,
                new RecordingExecutionRuntime(),
                event -> {},
                new SupervisorContextFactory(NoOpMemoryCompressors.noop()),
                documentToolAccessPolicy(100)
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-7",
                "session-7",
                AgentType.SUPERVISOR,
                new DocumentSnapshot("doc-7", "Title", "body"),
                "Improve this document",
                5
        ));

        ChatTranscriptMemory memory = (ChatTranscriptMemory) result.getMemory();
        assertTrue(memory.getMessages().stream().anyMatch(message -> "Improve this document".equals(message.getText())));
    }

    @Test
    void shouldCapResearcherWorkerIterationsAtFour() {
        WorkerRegistry workerRegistry = new WorkerRegistry();
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                "researcher",
                "Researcher",
                "Gather evidence",
                new StubWorkerAgent("research complete"),
                List.of("retrieveKnowledge"),
                List.of("research"),
                ExecutionToolAccessRole.RESEARCH
        ));
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                "editor",
                "Editor",
                "Apply document edits",
                new StubWorkerAgent("edited content"),
                List.of("editDocument")
        ));

        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        SupervisorOrchestrator orchestrator = new SupervisorOrchestrator(
                new ResearchThenEditSupervisorAgent(),
                new RecordingSupervisorExecutionRuntime(),
                workerRegistry,
                runtime,
                event -> {},
                new SupervisorContextFactory(NoOpMemoryCompressors.noop()),
                documentToolAccessPolicy(100)
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-6",
                "session-6",
                AgentType.SUPERVISOR,
                new DocumentSnapshot("doc-6", "Title", "body"),
                "Ground and edit this document",
                12
        ));

        assertEquals(TaskStatus.COMPLETED, result.getStatus());
        assertEquals(List.of("researcher", "editor"), runtime.workerIds());
        assertEquals(List.of(4, 12), runtime.maxIterations());
    }

    @Test
    void shouldUseIncrementalToolsForLongDocumentWriterAndReviewer() {
        WorkerRegistry workerRegistry = new WorkerRegistry();
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                SupervisorWorkerIds.WRITER,
                "Writer",
                "Apply document edits",
                new StubWorkerAgent("edited content"),
                List.of(DocumentToolNames.EDIT_DOCUMENT),
                List.of("write", "edit"),
                ExecutionToolAccessRole.MAIN_WRITE
        ));
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                SupervisorWorkerIds.REVIEWER,
                "Reviewer",
                "Review the document",
                new StubWorkerAgent("review complete"),
                List.of(DocumentToolNames.GET_DOCUMENT_SNAPSHOT),
                List.of("review"),
                ExecutionToolAccessRole.REVIEW
        ));

        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        SupervisorOrchestrator orchestrator = new SupervisorOrchestrator(
                new WriterThenReviewerSupervisorAgent(),
                new RecordingSupervisorExecutionRuntime(),
                workerRegistry,
                runtime,
                event -> {},
                new SupervisorContextFactory(NoOpMemoryCompressors.noop()),
                documentToolAccessPolicy(10)
        );

        orchestrator.execute(new TaskRequest(
                "task-8",
                "session-8",
                AgentType.SUPERVISOR,
                new DocumentSnapshot("doc-8", "Title", "x".repeat(80)),
                "Improve this document",
                5
        ));

        assertEquals(List.of(
                DocumentToolNames.READ_DOCUMENT_NODE,
                DocumentToolNames.PATCH_DOCUMENT_NODE,
                DocumentToolNames.SEARCH_CONTENT,
                MemoryToolNames.SEARCH_MEMORY
        ), runtime.allowedTools().get(0));
        assertEquals(List.of(
                DocumentToolNames.READ_DOCUMENT_NODE,
                DocumentToolNames.SEARCH_CONTENT,
                DocumentToolNames.ANALYZE_DOCUMENT
        ), runtime.allowedTools().get(1));
    }

    @Test
    void shouldAssignMemoryToolsToMemoryWorker() {
        WorkerRegistry workerRegistry = new WorkerRegistry();
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                SupervisorWorkerIds.MEMORY,
                "Memory",
                "Retrieve and maintain durable document constraints",
                new StubWorkerAgent("memory summary"),
                List.of(),
                List.of("memory"),
                ExecutionToolAccessRole.MEMORY
        ));
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                SupervisorWorkerIds.WRITER,
                "Writer",
                "Apply document edits",
                new StubWorkerAgent("edited content"),
                List.of(DocumentToolNames.EDIT_DOCUMENT),
                List.of("write", "edit"),
                ExecutionToolAccessRole.MAIN_WRITE
        ));

        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        SupervisorOrchestrator orchestrator = new SupervisorOrchestrator(
                new MemoryThenWriterSupervisorAgent(),
                new RecordingSupervisorExecutionRuntime(),
                workerRegistry,
                runtime,
                event -> {},
                new SupervisorContextFactory(NoOpMemoryCompressors.noop()),
                documentToolAccessPolicy(100),
                executionToolAccessPolicy(100)
        );

        orchestrator.execute(new TaskRequest(
                "task-9",
                "session-9",
                AgentType.SUPERVISOR,
                new DocumentSnapshot("doc-9", "Title", "body"),
                "Retrieve prior constraints before editing",
                5
        ));

        assertEquals(List.of(
                MemoryToolNames.SEARCH_MEMORY,
                MemoryToolNames.UPSERT_MEMORY
        ), runtime.allowedTools().get(0));
        assertEquals(List.of(
                DocumentToolNames.EDIT_DOCUMENT,
                DocumentToolNames.APPEND_TO_DOCUMENT,
                DocumentToolNames.GET_DOCUMENT_SNAPSHOT,
                DocumentToolNames.SEARCH_CONTENT,
                MemoryToolNames.SEARCH_MEMORY
        ), runtime.allowedTools().get(1));
    }

    @Test
    void shouldPassMemoryWorkerSummaryIntoLaterWriterContext() {
        WorkerRegistry workerRegistry = new WorkerRegistry();
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                SupervisorWorkerIds.MEMORY,
                "Memory",
                "Retrieve and maintain durable document constraints",
                new StubWorkerAgent("memory summary"),
                List.of(MemoryToolNames.SEARCH_MEMORY, MemoryToolNames.UPSERT_MEMORY),
                List.of("memory"),
                ExecutionToolAccessRole.MEMORY
        ));
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                SupervisorWorkerIds.WRITER,
                "Writer",
                "Apply document edits",
                new StubWorkerAgent("edited content"),
                List.of(DocumentToolNames.EDIT_DOCUMENT),
                List.of("write", "edit"),
                ExecutionToolAccessRole.MAIN_WRITE
        ));

        MemorySummaryExecutionRuntime runtime = new MemorySummaryExecutionRuntime();
        SupervisorOrchestrator orchestrator = new SupervisorOrchestrator(
                new MemoryThenWriterSupervisorAgent(),
                new RecordingSupervisorExecutionRuntime(),
                workerRegistry,
                runtime,
                event -> {},
                new SupervisorContextFactory(NoOpMemoryCompressors.noop()),
                documentToolAccessPolicy(100),
                executionToolAccessPolicy(100)
        );

        orchestrator.execute(new TaskRequest(
                "task-10",
                "session-10",
                AgentType.SUPERVISOR,
                new DocumentSnapshot("doc-10", "Title", "body"),
                "Retrieve prior constraints before editing",
                5
        ));

        ChatTranscriptMemory writerMemory = (ChatTranscriptMemory) runtime.states().get(1).getMemory();
        assertTrue(writerMemory.getMessages().stream().anyMatch(message ->
                message.getText().contains("confirmedConstraints")
        ));
        assertTrue(writerMemory.getMessages().stream().anyMatch(message ->
                message.getText().contains("guidanceForDownstreamWorkers")
        ));
        assertTrue(writerMemory.getMessages().stream().noneMatch(ChatMessage.ToolExecutionResultChatMessage.class::isInstance));
    }

    private DocumentToolAccessPolicy documentToolAccessPolicy(int threshold) {
        return new DocumentToolAccessPolicy(
                new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 4_000, 1_200),
                new DocumentToolModeProperties(threshold)
        );
    }

    private ExecutionToolAccessPolicy executionToolAccessPolicy(int threshold) {
        return new ExecutionToolAccessPolicy(
                documentToolAccessPolicy(threshold),
                new MemoryToolAccessPolicy()
        );
    }

    private static final class ScriptedSupervisorAgentDefinition implements SupervisorAgent {

        @Override
        public AgentType type() {
            return AgentType.SUPERVISOR;
        }

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

    private static final class RepeatingWorkerSupervisorAgentDefinition implements SupervisorAgent {

        private final List<SupervisorContext> contexts = new ArrayList<>();

        @Override
        public AgentType type() {
            return AgentType.SUPERVISOR;
        }

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

    private static final class ResearchThenEditSupervisorAgent implements SupervisorAgent {

        @Override
        public AgentType type() {
            return AgentType.SUPERVISOR;
        }

        @Override
        public SupervisorDecision decide(SupervisorContext context) {
            if (context.getWorkerResults().isEmpty()) {
                return new SupervisorDecision.AssignWorker("researcher", "Gather evidence", "start with research");
            }
            if (context.getWorkerResults().size() == 1) {
                return new SupervisorDecision.AssignWorker("editor", "Apply the recommended edits", "move to editing");
            }
            return new SupervisorDecision.Complete(context.getCurrentContent(), "workers done", "finalized by supervisor");
        }
    }

    private static final class WriterThenReviewerSupervisorAgent implements SupervisorAgent {

        @Override
        public AgentType type() {
            return AgentType.SUPERVISOR;
        }

        @Override
        public SupervisorDecision decide(SupervisorContext context) {
            if (context.getWorkerResults().isEmpty()) {
                return new SupervisorDecision.AssignWorker(SupervisorWorkerIds.WRITER, "Apply the recommended edits", "move to editing");
            }
            if (context.getWorkerResults().size() == 1) {
                return new SupervisorDecision.AssignWorker(SupervisorWorkerIds.REVIEWER, "Review the updated content", "move to review");
            }
            return new SupervisorDecision.Complete(context.getCurrentContent(), "workers done", "finalized by supervisor");
        }
    }

    private static final class MemoryThenWriterSupervisorAgent implements SupervisorAgent {

        @Override
        public AgentType type() {
            return AgentType.SUPERVISOR;
        }

        @Override
        public SupervisorDecision decide(SupervisorContext context) {
            if (context.getWorkerResults().isEmpty()) {
                return new SupervisorDecision.AssignWorker(SupervisorWorkerIds.MEMORY, "Retrieve durable document constraints", "consult memory");
            }
            if (context.getWorkerResults().size() == 1) {
                return new SupervisorDecision.AssignWorker(SupervisorWorkerIds.WRITER, "Apply the recommended edits", "move to editing");
            }
            return new SupervisorDecision.Complete(context.getCurrentContent(), "workers done", "finalized by supervisor");
        }
    }

    private static SupervisorContext supervisorContext(String taskId,
                                                       String sessionId,
                                                       String instruction,
                                                       String currentContent,
                                                       List<SupervisorContext.WorkerDefinition> availableWorkers,
                                                       List<SupervisorContext.WorkerResult> workerResults) {
        SupervisorContext context = SupervisorContext.builder()
                .request(new ExecutionRequest(
                        taskId,
                        sessionId,
                        AgentType.SUPERVISOR,
                        new DocumentSnapshot("doc-" + taskId, "Title", currentContent),
                        instruction,
                        5
                ))
                .iteration(0)
                .currentContent(currentContent)
                .memory(new ChatTranscriptMemory(List.of()))
                .stage(ExecutionStage.RUNNING)
                .pendingReason(null)
                .toolSpecifications(List.of())
                .availableWorkers(availableWorkers)
                .workerResults(workerResults)
                .build();
        return context;
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

    private static class RecordingExecutionRuntime implements ExecutionRuntime {

        protected final List<ExecutionRequest> requests = new ArrayList<>();
        protected final List<AgentRunContext> states = new ArrayList<>();

        @Override
        public ExecutionResult run(Agent agent, ExecutionRequest request) {
            requests.add(request);
            String marker = markerFor(request);
            return new ExecutionResult(marker + " result", request.getDocument().getContent() + " -> " + marker);
        }

        @Override
        public ExecutionResult run(Agent definition, ExecutionRequest request, AgentRunContext initialState) {
            requests.add(request);
            states.add(initialState);
            String marker = markerFor(request);
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

        private String markerFor(ExecutionRequest request) {
            if (SupervisorWorkerIds.WRITER.equals(request.getWorkerId())
                    || request.getAllowedTools().contains(DocumentToolNames.EDIT_DOCUMENT)
                    || request.getAllowedTools().contains(DocumentToolNames.PATCH_DOCUMENT_NODE)) {
                return "editor";
            }
            if (SupervisorWorkerIds.REVIEWER.equals(request.getWorkerId())) {
                return "reviewer";
            }
            return "analyzer";
        }

        private List<String> workerIds() {
            return requests.stream().map(ExecutionRequest::getWorkerId).toList();
        }

        private List<List<String>> allowedTools() {
            return requests.stream().map(ExecutionRequest::getAllowedTools).toList();
        }

        private List<Integer> maxIterations() {
            return requests.stream().map(ExecutionRequest::getMaxIterations).toList();
        }

        protected List<AgentRunContext> states() {
            return states;
        }
    }

    private static final class MemorySummaryExecutionRuntime extends RecordingExecutionRuntime {

        @Override
        public ExecutionResult run(Agent definition, ExecutionRequest request, AgentRunContext initialState) {
            if (SupervisorWorkerIds.MEMORY.equals(request.getWorkerId())) {
                requests.add(request);
                states.add(initialState);
                return new ExecutionResult(
                        "memory summary",
                        """
                        {"confirmedConstraints":["keep title hierarchy"],"deprecatedConstraints":[],"activeRisks":[],"guidanceForDownstreamWorkers":"Preserve the current outline."}
                        """,
                        initialState.getCurrentContent(),
                        new AgentRunContext(
                                request,
                                initialState.getIteration() + 1,
                                initialState.getCurrentContent(),
                                new ChatTranscriptMemory(List.of(
                                        new ChatMessage.ToolExecutionResultChatMessage(
                                                "memory-tool-call",
                                                MemoryToolNames.SEARCH_MEMORY,
                                                null,
                                                "memory search finished"
                                        )
                                )),
                                ExecutionStage.COMPLETED,
                                null,
                                List.of()
                        )
                );
            }
            return super.run(definition, request, initialState);
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

    private static final class RecordingSupervisorContextFactory extends SupervisorContextFactory {

        private int supervisorContextBuildCount;
        private int workerExecutionContextBuildCount;
        private int workerSummaryCount;

        private RecordingSupervisorContextFactory() {
            super(NoOpMemoryCompressors.noop());
        }

        @Override
        public SupervisorContext buildSupervisorContext(TaskRequest request,
                                                        AgentRunContext conversationState,
                                                        List<SupervisorContext.WorkerResult> workerResults,
                                                        List<SupervisorContext.WorkerDefinition> availableWorkers) {
            supervisorContextBuildCount++;
            return super.buildSupervisorContext(request, conversationState, workerResults, availableWorkers);
        }

        @Override
        public AgentRunContext buildWorkerExecutionContext(AgentRunContext conversationState,
                                                           String currentContent,
                                                           String instruction) {
            workerExecutionContextBuildCount++;
            return super.buildWorkerExecutionContext(conversationState, currentContent, instruction);
        }

        @Override
        public AgentRunContext summarizeWorkerResult(AgentRunContext conversationState,
                                                     String workerId,
                                                     ExecutionResult<?> result) {
            workerSummaryCount++;
            return super.summarizeWorkerResult(conversationState, workerId, result);
        }
    }

    private static final class RecordingSupervisorExecutionRuntime extends SupervisorExecutionRuntime {

        private int runCount;

        private RecordingSupervisorExecutionRuntime() {
            super(event -> {});
        }

        @Override
        public ExecutionResult<SupervisorDecision> run(Agent agent, ExecutionRequest request, AgentRunContext initialContext) {
            runCount++;
            return super.run(agent, request, initialContext);
        }
    }
}
