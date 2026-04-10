package com.agent.editor.service;

import com.agent.editor.agent.core.agent.AgentType;
import com.agent.editor.agent.core.state.TaskStatus;
import com.agent.editor.agent.event.EventType;
import com.agent.editor.agent.event.EventPublisher;
import com.agent.editor.agent.tool.memory.MemoryUpsertAction;
import com.agent.editor.agent.task.TaskOrchestrator;
import com.agent.editor.agent.task.TaskRequest;
import com.agent.editor.agent.task.TaskResult;
import com.agent.editor.agent.core.memory.LongTermMemoryItem;
import com.agent.editor.agent.core.memory.LongTermMemoryType;
import com.agent.editor.dto.AgentTaskRequest;
import com.agent.editor.dto.AgentTaskResponse;
import com.agent.editor.dto.PendingDocumentChange;
import com.agent.editor.dto.UserProfileMemoryRequest;
import com.agent.editor.dto.UserProfileMemoryResponse;
import com.agent.editor.repository.LongTermMemoryRepository;
import com.agent.editor.model.AgentMode;
import com.agent.editor.websocket.WebSocketService;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static com.agent.editor.testsupport.AgentTestFixtures.emptyProvider;
import static com.agent.editor.testsupport.AgentTestFixtures.singletonProvider;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskApplicationServiceTest {

    @Test
    void shouldExecuteTaskThroughDedicatedApplicationService() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        TaskOrchestrator orchestrator = new StubTaskOrchestrator();
        TaskApplicationService service = newTaskApplicationService(
                documentService,
                queryService,
                diffService,
                pendingChangeService,
                orchestrator,
                mock(LongTermMemoryRetrievalService.class),
                new UserProfilePromptAssembler(),
                mock(WebSocketService.class),
                mock(EventPublisher.class),
                directTaskExecutor()
        );

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-001");
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);
        String originalContent = documentService.getDocument("doc-001").getContent();

        AgentTaskResponse response = service.execute(request);

        assertNotNull(response.getTaskId());
        assertEquals("COMPLETED", response.getStatus());
        assertEquals("rewritten content", response.getFinalResult());
        assertEquals(originalContent, documentService.getDocument("doc-001").getContent());
        assertEquals("rewritten content", pendingChangeService.getPendingChange("doc-001").getProposedContent());
        assertEquals("COMPLETED", service.getTaskStatus(response.getTaskId()).getStatus());
    }

    @Test
    void shouldLoadConfirmedProfilesIntoTaskRequestBeforeExecution() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        CapturingTaskOrchestrator orchestrator = new CapturingTaskOrchestrator();
        LongTermMemoryRetrievalService retrievalService = mock(LongTermMemoryRetrievalService.class);
        when(retrievalService.loadConfirmedProfiles()).thenReturn(List.of(profile("Always answer in Chinese")));
        TaskApplicationService service = newTaskApplicationService(
                documentService,
                queryService,
                diffService,
                pendingChangeService,
                orchestrator,
                retrievalService,
                new UserProfilePromptAssembler(),
                mock(WebSocketService.class),
                mock(EventPublisher.class),
                directTaskExecutor()
        );

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-001");
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);

        service.execute(request);

        assertTrue(orchestrator.lastRequest.getUserProfileGuidance().contains("Always answer in Chinese"));
    }

    @Test
    void shouldManageUserProfilesThroughApplicationService() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        LongTermMemoryWriteService writeService = mock(LongTermMemoryWriteService.class);
        LongTermMemoryRepository repository = mock(LongTermMemoryRepository.class);
        when(repository.listUserProfiles()).thenReturn(List.of(profile("Always answer in Chinese")));
        when(writeService.upsert(MemoryUpsertAction.CREATE, LongTermMemoryType.USER_PROFILE, null, null, "Always answer in Chinese"))
                .thenReturn(profile("Always answer in Chinese"));
        when(writeService.upsert(MemoryUpsertAction.REPLACE, LongTermMemoryType.USER_PROFILE, "memory-profile", null, "Prefer concise summaries"))
                .thenReturn(profile("Prefer concise summaries"));
        TaskApplicationService service = newTaskApplicationService(
                documentService,
                queryService,
                diffService,
                pendingChangeService,
                new StubTaskOrchestrator(),
                mock(LongTermMemoryRetrievalService.class),
                new UserProfilePromptAssembler(),
                writeService,
                repository,
                mock(WebSocketService.class),
                mock(EventPublisher.class),
                directTaskExecutor()
        );

        UserProfileMemoryRequest createRequest = new UserProfileMemoryRequest();
        createRequest.setSummary("Always answer in Chinese");
        UserProfileMemoryRequest updateRequest = new UserProfileMemoryRequest();
        updateRequest.setSummary("Prefer concise summaries");

        List<UserProfileMemoryResponse> listedProfiles = service.listUserProfiles();
        UserProfileMemoryResponse created = service.createUserProfile(createRequest);
        UserProfileMemoryResponse updated = service.updateUserProfile("memory-profile", updateRequest);
        service.deleteUserProfile("memory-profile");

        assertEquals(1, listedProfiles.size());
        assertEquals("Always answer in Chinese", listedProfiles.get(0).getSummary());
        assertEquals("Always answer in Chinese", created.getSummary());
        assertEquals("Prefer concise summaries", updated.getSummary());
        verify(writeService).upsert(MemoryUpsertAction.CREATE, LongTermMemoryType.USER_PROFILE, null, null, "Always answer in Chinese");
        verify(writeService).upsert(MemoryUpsertAction.REPLACE, LongTermMemoryType.USER_PROFILE, "memory-profile", null, "Prefer concise summaries");
        verify(writeService).upsert(MemoryUpsertAction.DELETE, LongTermMemoryType.USER_PROFILE, "memory-profile", null, null);
        verify(repository, times(1)).listUserProfiles();
    }

    @Test
    void shouldSubmitAsyncTaskAndReturnRunningStatusBeforeBackgroundExecutionCompletes() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        TaskOrchestrator orchestrator = new StubTaskOrchestrator();
        CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
        TaskApplicationService service = newTaskApplicationService(
                documentService,
                queryService,
                diffService,
                pendingChangeService,
                orchestrator,
                mock(WebSocketService.class),
                mock(EventPublisher.class),
                taskExecutor
        );

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-001");
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);
        String originalContent = documentService.getDocument("doc-001").getContent();

        AgentTaskResponse response = service.executeAsync(request);

        assertNotNull(response.getTaskId());
        assertEquals("RUNNING", response.getStatus());
        assertNull(response.getFinalResult());
        assertEquals(originalContent, documentService.getDocument("doc-001").getContent());
        assertEquals("RUNNING", service.getTaskStatus(response.getTaskId()).getStatus());

        taskExecutor.runNext();

        assertEquals(originalContent, documentService.getDocument("doc-001").getContent());
        assertEquals("rewritten content", pendingChangeService.getPendingChange("doc-001").getProposedContent());
        assertEquals("COMPLETED", service.getTaskStatus(response.getTaskId()).getStatus());
    }

    @Test
    void shouldNotCreatePendingChangeWhenAgentReturnsUnchangedDocument() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        String originalContent = documentService.getDocument("doc-001").getContent();
        pendingChangeService.savePendingChange("doc-001", "old-task", originalContent, "stale pending draft");
        TaskApplicationService service = newTaskApplicationService(
                documentService,
                queryService,
                diffService,
                pendingChangeService,
                request -> new TaskResult(TaskStatus.COMPLETED, originalContent),
                mock(WebSocketService.class),
                mock(EventPublisher.class),
                directTaskExecutor()
        );

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-001");
        request.setInstruction("review without changing");
        request.setMode(AgentMode.REACT);

        AgentTaskResponse response = service.execute(request);

        assertEquals("COMPLETED", response.getStatus());
        assertEquals(originalContent, response.getFinalResult());
        assertNull(pendingChangeService.getPendingChange("doc-001"));
        assertEquals(0, diffService.getDiffHistory("doc-001").size());
    }

    @Test
    void shouldApplyPendingChangeOnlyAfterExplicitConfirmation() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        TaskApplicationService service = newTaskApplicationService(
                documentService,
                queryService,
                diffService,
                pendingChangeService,
                new StubTaskOrchestrator(),
                mock(WebSocketService.class),
                mock(EventPublisher.class),
                directTaskExecutor()
        );
        String originalContent = documentService.getDocument("doc-001").getContent();

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-001");
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);

        service.execute(request);

        PendingDocumentChange pendingChange = service.applyPendingDocumentChange("doc-001");

        assertEquals("rewritten content", documentService.getDocument("doc-001").getContent());
        assertEquals("rewritten content", pendingChange.getProposedContent());
        assertNull(service.getPendingDocumentChange("doc-001"));
        assertEquals(1, diffService.getDiffHistory("doc-001").size());
        assertEquals(originalContent, diffService.getDiffHistory("doc-001").get(0).getOriginalContent());
    }

    @Test
    void shouldDiscardPendingChangeWithoutMutatingDocument() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        TaskApplicationService service = newTaskApplicationService(
                documentService,
                queryService,
                diffService,
                pendingChangeService,
                new StubTaskOrchestrator(),
                mock(WebSocketService.class),
                mock(EventPublisher.class),
                directTaskExecutor()
        );
        String originalContent = documentService.getDocument("doc-001").getContent();

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-001");
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);

        service.execute(request);
        service.discardPendingDocumentChange("doc-001");

        assertEquals(originalContent, documentService.getDocument("doc-001").getContent());
        assertNull(service.getPendingDocumentChange("doc-001"));
        assertEquals(0, diffService.getDiffHistory("doc-001").size());
    }

    @Test
    void shouldExecuteAsyncTaskAgainstSubmissionSnapshotInsteadOfLaterDocumentMutations() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        CapturingTaskOrchestrator orchestrator = new CapturingTaskOrchestrator();
        CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
        TaskApplicationService service = newTaskApplicationService(
                documentService,
                queryService,
                diffService,
                pendingChangeService,
                orchestrator,
                mock(WebSocketService.class),
                mock(EventPublisher.class),
                taskExecutor
        );

        String originalContent = documentService.getDocument("doc-001").getContent();
        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-001");
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);

        service.executeAsync(request);
        documentService.updateDocument("doc-001", "external mutation");

        taskExecutor.runNext();

        assertEquals(originalContent, orchestrator.lastRequest.getDocument().getContent());
    }

    @Test
    void shouldMapSupervisorModeToSupervisorAgentType() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        CapturingTaskOrchestrator orchestrator = new CapturingTaskOrchestrator();
        TaskApplicationService service = newTaskApplicationService(
                documentService,
                queryService,
                diffService,
                pendingChangeService,
                orchestrator,
                mock(WebSocketService.class),
                mock(EventPublisher.class),
                directTaskExecutor()
        );

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-001");
        request.setInstruction("coordinate workers");
        request.setMode(AgentMode.SUPERVISOR);

        service.execute(request);

        assertEquals(AgentType.SUPERVISOR, orchestrator.lastRequest.getAgentType());
    }

    @Test
    void shouldMapReflexionModeToReflexionAgentType() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        CapturingTaskOrchestrator orchestrator = new CapturingTaskOrchestrator();
        TaskApplicationService service = newTaskApplicationService(
                documentService,
                queryService,
                diffService,
                pendingChangeService,
                orchestrator,
                mock(WebSocketService.class),
                mock(EventPublisher.class),
                directTaskExecutor()
        );

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-001");
        request.setInstruction("revise with critique");
        request.setMode(AgentMode.REFLEXION);

        service.execute(request);

        assertEquals(AgentType.REFLEXION, orchestrator.lastRequest.getAgentType());
    }

    @Test
    void shouldBindWebSocketSessionToTaskBeforeExecutionWhenSessionIdProvided() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        TaskOrchestrator orchestrator = mock(TaskOrchestrator.class);
        when(orchestrator.execute(any(TaskRequest.class))).thenReturn(new TaskResult(TaskStatus.COMPLETED, "rewritten content"));
        WebSocketService webSocketService = mock(WebSocketService.class);
        TaskApplicationService service = newTaskApplicationService(
                documentService,
                queryService,
                diffService,
                pendingChangeService,
                orchestrator,
                webSocketService,
                mock(EventPublisher.class),
                directTaskExecutor()
        );

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-001");
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);
        request.setSessionId("ws-session-1");

        AgentTaskResponse response = service.execute(request);

        var order = inOrder(webSocketService, orchestrator);
        order.verify(webSocketService).bindTaskToSession("ws-session-1", response.getTaskId());
        order.verify(orchestrator).execute(any(TaskRequest.class));
    }

    @Test
    void shouldBindWebSocketSessionToAsyncTaskBeforeExecutionWhenSessionIdProvided() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        TaskOrchestrator orchestrator = mock(TaskOrchestrator.class);
        when(orchestrator.execute(any(TaskRequest.class))).thenReturn(new TaskResult(TaskStatus.COMPLETED, "rewritten content"));
        WebSocketService webSocketService = mock(WebSocketService.class);
        CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
        TaskApplicationService service = newTaskApplicationService(
                documentService,
                queryService,
                diffService,
                pendingChangeService,
                orchestrator,
                webSocketService,
                mock(EventPublisher.class),
                taskExecutor
        );

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-001");
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);
        request.setSessionId("ws-session-async");

        AgentTaskResponse response = service.executeAsync(request);

        var order = inOrder(webSocketService, orchestrator);
        order.verify(webSocketService).bindTaskToSession("ws-session-async", response.getTaskId());
        taskExecutor.runNext();
        order.verify(orchestrator).execute(any(TaskRequest.class));
    }

    @Test
    void shouldNotBindWebSocketWhenSessionIdMissing() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        TaskOrchestrator orchestrator = new StubTaskOrchestrator();
        WebSocketService webSocketService = mock(WebSocketService.class);
        TaskApplicationService service = newTaskApplicationService(
                documentService,
                queryService,
                diffService,
                pendingChangeService,
                orchestrator,
                webSocketService,
                mock(EventPublisher.class),
                directTaskExecutor()
        );

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-001");
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);

        service.execute(request);

        verifyNoInteractions(webSocketService);
    }

    @Test
    void shouldMarkAsyncTaskFailedWhenBackgroundExecutionThrows() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        TaskOrchestrator orchestrator = request -> {
            throw new IllegalStateException("model timeout");
        };
        EventPublisher eventPublisher = mock(EventPublisher.class);
        CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
        TaskApplicationService service = newTaskApplicationService(
                documentService,
                queryService,
                diffService,
                pendingChangeService,
                orchestrator,
                mock(WebSocketService.class),
                eventPublisher,
                taskExecutor
        );

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-001");
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);

        AgentTaskResponse response = service.executeAsync(request);

        taskExecutor.runNext();

        assertEquals("FAILED", service.getTaskStatus(response.getTaskId()).getStatus());
        verify(eventPublisher).publish(argThat(event ->
                event.getType() == EventType.TASK_FAILED
                        && response.getTaskId().equals(event.getTaskId())
                        && "model timeout".equals(event.getMessage())));
    }

    @Test
    void shouldRemoveSubmittedTaskStateWhenDispatchRejected() {
        DocumentService documentService = new DocumentService();
        TrackingTaskQueryService queryService = new TrackingTaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        TaskOrchestrator orchestrator = new StubTaskOrchestrator();
        WebSocketService webSocketService = mock(WebSocketService.class);
        TaskApplicationService service = newTaskApplicationService(
                documentService,
                queryService,
                diffService,
                pendingChangeService,
                orchestrator,
                webSocketService,
                mock(EventPublisher.class),
                task -> {
                    throw new IllegalStateException("queue full");
                }
        );

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-001");
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);
        request.setSessionId("ws-session-async");

        try {
            service.executeAsync(request);
        } catch (IllegalStateException ex) {
            assertEquals("Failed to submit agent task", ex.getMessage());
        }

        assertEquals(queryService.lastSavedTaskId, queryService.lastRemovedTaskId);
        assertNull(queryService.findById(queryService.lastSavedTaskId));
        var order = inOrder(webSocketService);
        order.verify(webSocketService).bindTaskToSession("ws-session-async", queryService.lastSavedTaskId);
        order.verify(webSocketService).unbindTaskFromSession("ws-session-async", queryService.lastSavedTaskId);
    }

    private static final class StubTaskOrchestrator implements TaskOrchestrator {

        @Override
        public TaskResult execute(TaskRequest request) {
            return new TaskResult(TaskStatus.COMPLETED, "rewritten content");
        }
    }

    private static final class CapturingTaskOrchestrator implements TaskOrchestrator {

        private TaskRequest lastRequest;

        @Override
        public TaskResult execute(TaskRequest request) {
            this.lastRequest = request;
            return new TaskResult(TaskStatus.COMPLETED, "supervised content");
        }
    }

    private static TaskExecutor directTaskExecutor() {
        return Runnable::run;
    }

    private static TaskApplicationService newTaskApplicationService(DocumentService documentService,
                                                                    TaskQueryService taskQueryService,
                                                                    DiffService diffService,
                                                                    PendingDocumentChangeService pendingDocumentChangeService,
                                                                    TaskOrchestrator taskOrchestrator,
                                                                    LongTermMemoryRetrievalService longTermMemoryRetrievalService,
                                                                    UserProfilePromptAssembler userProfilePromptAssembler,
                                                                    WebSocketService webSocketService,
                                                                    EventPublisher eventPublisher,
                                                                    TaskExecutor taskExecutor) {
        return new TaskApplicationService(
                documentService,
                taskQueryService,
                diffService,
                pendingDocumentChangeService,
                taskOrchestrator,
                longTermMemoryRetrievalService,
                userProfilePromptAssembler,
                emptyProvider(),
                emptyProvider(),
                webSocketService,
                eventPublisher,
                taskExecutor
        );
    }

    private static TaskApplicationService newTaskApplicationService(DocumentService documentService,
                                                                    TaskQueryService taskQueryService,
                                                                    DiffService diffService,
                                                                    PendingDocumentChangeService pendingDocumentChangeService,
                                                                    TaskOrchestrator taskOrchestrator,
                                                                    LongTermMemoryRetrievalService longTermMemoryRetrievalService,
                                                                    UserProfilePromptAssembler userProfilePromptAssembler,
                                                                    LongTermMemoryWriteService longTermMemoryWriteService,
                                                                    LongTermMemoryRepository longTermMemoryRepository,
                                                                    WebSocketService webSocketService,
                                                                    EventPublisher eventPublisher,
                                                                    TaskExecutor taskExecutor) {
        return new TaskApplicationService(
                documentService,
                taskQueryService,
                diffService,
                pendingDocumentChangeService,
                taskOrchestrator,
                longTermMemoryRetrievalService,
                userProfilePromptAssembler,
                singletonProvider(longTermMemoryWriteService),
                singletonProvider(longTermMemoryRepository),
                webSocketService,
                eventPublisher,
                taskExecutor
        );
    }

    private static TaskApplicationService newTaskApplicationService(DocumentService documentService,
                                                                    TaskQueryService taskQueryService,
                                                                    DiffService diffService,
                                                                    PendingDocumentChangeService pendingDocumentChangeService,
                                                                    TaskOrchestrator taskOrchestrator,
                                                                    WebSocketService webSocketService,
                                                                    EventPublisher eventPublisher,
                                                                    TaskExecutor taskExecutor) {
        return new TaskApplicationService(
                documentService,
                taskQueryService,
                diffService,
                pendingDocumentChangeService,
                taskOrchestrator,
                null,
                new UserProfilePromptAssembler(),
                emptyProvider(),
                emptyProvider(),
                webSocketService,
                eventPublisher,
                taskExecutor
        );
    }

    private static LongTermMemoryItem profile(String summary) {
        return new LongTermMemoryItem(
                "memory-" + summary.hashCode(),
                LongTermMemoryType.USER_PROFILE,
                null,
                summary,
                summary,
                "task-1",
                "session-1",
                List.of("confirmed"),
                LocalDateTime.of(2026, 4, 3, 10, 0),
                LocalDateTime.of(2026, 4, 3, 10, 5),
                new float[]{0.1f, 0.2f}
        );
    }

    private static final class CapturingTaskExecutor implements TaskExecutor {

        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        private void runNext() {
            Runnable task = tasks.poll();
            if (task != null) {
                task.run();
            }
        }
    }

    private static final class TrackingTaskQueryService extends TaskQueryService {

        private String lastSavedTaskId;
        private String lastRemovedTaskId;

        @Override
        public void save(com.agent.editor.agent.core.state.TaskState state) {
            lastSavedTaskId = state.getTaskId();
            super.save(state);
        }

        @Override
        public void remove(String taskId) {
            lastRemovedTaskId = taskId;
            super.remove(taskId);
        }
    }
}
