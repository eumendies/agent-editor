package com.agent.editor.service;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.agent.v2.task.TaskResult;
import com.agent.editor.dto.AgentTaskRequest;
import com.agent.editor.dto.AgentTaskResponse;
import com.agent.editor.dto.PendingDocumentChange;
import com.agent.editor.model.AgentMode;
import com.agent.editor.websocket.WebSocketService;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
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
        TaskApplicationService service = new TaskApplicationService(
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
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);

        AgentTaskResponse response = service.execute(request);

        assertNotNull(response.getTaskId());
        assertEquals("COMPLETED", response.getStatus());
        assertEquals("rewritten content", response.getFinalResult());
        assertEquals("""
                从前，有一只小狐狸住在森林边缘。它毛色火红，眼睛明亮，对这个世界充满了好奇。
                
                狐狸妈妈总是叮嘱它："孩子，不要在森林里走得太远，外面很危险。"
                
                小狐狸点点头，但心里却想着：森林外面到底是什么样子的呢？
                """, documentService.getDocument("doc-001").getContent());
        assertEquals("rewritten content", pendingChangeService.getPendingChange("doc-001").getProposedContent());
        assertEquals("COMPLETED", service.getTaskStatus(response.getTaskId()).getStatus());
    }

    @Test
    void shouldSubmitV2TaskAndReturnRunningStatusBeforeBackgroundExecutionCompletes() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        TaskOrchestrator orchestrator = new StubTaskOrchestrator();
        CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
        TaskApplicationService service = new TaskApplicationService(
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

        AgentTaskResponse response = service.executeV2(request);

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
    void shouldApplyPendingChangeOnlyAfterExplicitConfirmation() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        TaskApplicationService service = new TaskApplicationService(
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
        TaskApplicationService service = new TaskApplicationService(
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
    void shouldExecuteV2TaskAgainstSubmissionSnapshotInsteadOfLaterDocumentMutations() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        CapturingTaskOrchestrator orchestrator = new CapturingTaskOrchestrator();
        CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
        TaskApplicationService service = new TaskApplicationService(
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

        service.executeV2(request);
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
        TaskApplicationService service = new TaskApplicationService(
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
        TaskApplicationService service = new TaskApplicationService(
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
        TaskApplicationService service = new TaskApplicationService(
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
    void shouldBindV2WebSocketSessionToTaskBeforeExecutionWhenSessionIdProvided() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        TaskOrchestrator orchestrator = mock(TaskOrchestrator.class);
        when(orchestrator.execute(any(TaskRequest.class))).thenReturn(new TaskResult(TaskStatus.COMPLETED, "rewritten content"));
        WebSocketService webSocketService = mock(WebSocketService.class);
        CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
        TaskApplicationService service = new TaskApplicationService(
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
        request.setSessionId("ws-session-v2");

        AgentTaskResponse response = service.executeV2(request);

        var order = inOrder(webSocketService, orchestrator);
        order.verify(webSocketService).bindV2TaskToSession("ws-session-v2", response.getTaskId());
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
        TaskApplicationService service = new TaskApplicationService(
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
    void shouldMarkV2TaskFailedWhenBackgroundExecutionThrows() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        PendingDocumentChangeService pendingChangeService = new PendingDocumentChangeService(diffService);
        TaskOrchestrator orchestrator = request -> {
            throw new IllegalStateException("model timeout");
        };
        EventPublisher eventPublisher = mock(EventPublisher.class);
        CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
        TaskApplicationService service = new TaskApplicationService(
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

        AgentTaskResponse response = service.executeV2(request);

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
        TaskApplicationService service = new TaskApplicationService(
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
        request.setSessionId("ws-session-v2");

        try {
            service.executeV2(request);
        } catch (IllegalStateException ex) {
            assertEquals("Failed to submit agent task", ex.getMessage());
        }

        assertEquals(queryService.lastSavedTaskId, queryService.lastRemovedTaskId);
        assertNull(queryService.findById(queryService.lastSavedTaskId));
        var order = inOrder(webSocketService);
        order.verify(webSocketService).bindV2TaskToSession("ws-session-v2", queryService.lastSavedTaskId);
        order.verify(webSocketService).unbindV2TaskFromSession("ws-session-v2", queryService.lastSavedTaskId);
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
        public void save(com.agent.editor.agent.v2.core.state.TaskState state) {
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
