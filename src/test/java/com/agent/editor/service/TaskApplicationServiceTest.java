package com.agent.editor.service;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.agent.v2.task.TaskResult;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.dto.AgentTaskRequest;
import com.agent.editor.dto.AgentTaskResponse;
import com.agent.editor.model.AgentMode;
import com.agent.editor.websocket.WebSocketService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class TaskApplicationServiceTest {

    @Test
    void shouldExecuteTaskThroughDedicatedApplicationService() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        TaskOrchestrator orchestrator = new StubTaskOrchestrator();
        TaskApplicationService service = new TaskApplicationService(
                documentService,
                queryService,
                diffService,
                orchestrator,
                mock(WebSocketService.class)
        );

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-001");
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);

        AgentTaskResponse response = service.execute(request);

        assertNotNull(response.getTaskId());
        assertEquals("COMPLETED", response.getStatus());
        assertEquals("rewritten content", response.getFinalResult());
        assertEquals("rewritten content", documentService.getDocument("doc-001").getContent());
        assertEquals("COMPLETED", service.getTaskStatus(response.getTaskId()).getStatus());
    }

    @Test
    void shouldMapSupervisorModeToSupervisorAgentType() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        CapturingTaskOrchestrator orchestrator = new CapturingTaskOrchestrator();
        TaskApplicationService service = new TaskApplicationService(
                documentService,
                queryService,
                diffService,
                orchestrator,
                mock(WebSocketService.class)
        );

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-001");
        request.setInstruction("coordinate workers");
        request.setMode(AgentMode.SUPERVISOR);

        service.execute(request);

        assertEquals(AgentType.SUPERVISOR, orchestrator.lastRequest.agentType());
    }

    @Test
    void shouldMapReflexionModeToReflexionAgentType() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        CapturingTaskOrchestrator orchestrator = new CapturingTaskOrchestrator();
        TaskApplicationService service = new TaskApplicationService(
                documentService,
                queryService,
                diffService,
                orchestrator,
                mock(WebSocketService.class)
        );

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-001");
        request.setInstruction("revise with critique");
        request.setMode(AgentMode.REFLEXION);

        service.execute(request);

        assertEquals(AgentType.REFLEXION, orchestrator.lastRequest.agentType());
    }

    @Test
    void shouldBindWebSocketSessionToTaskBeforeExecutionWhenSessionIdProvided() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        TaskOrchestrator orchestrator = mock(TaskOrchestrator.class);
        when(orchestrator.execute(any(TaskRequest.class))).thenReturn(new TaskResult(TaskStatus.COMPLETED, "rewritten content"));
        WebSocketService webSocketService = mock(WebSocketService.class);
        TaskApplicationService service = new TaskApplicationService(
                documentService,
                queryService,
                diffService,
                orchestrator,
                webSocketService
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
    void shouldNotBindWebSocketWhenSessionIdMissing() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        TaskOrchestrator orchestrator = new StubTaskOrchestrator();
        WebSocketService webSocketService = mock(WebSocketService.class);
        TaskApplicationService service = new TaskApplicationService(
                documentService,
                queryService,
                diffService,
                orchestrator,
                webSocketService
        );

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-001");
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);

        service.execute(request);

        verifyNoInteractions(webSocketService);
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
}
