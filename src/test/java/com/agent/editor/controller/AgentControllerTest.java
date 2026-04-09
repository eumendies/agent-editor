package com.agent.editor.controller;

import com.agent.editor.agent.event.EventType;
import com.agent.editor.agent.event.ExecutionEvent;
import com.agent.editor.dto.AgentTaskRequest;
import com.agent.editor.dto.AgentTaskResponse;
import com.agent.editor.dto.SessionMemoryResponse;
import com.agent.editor.model.AgentMode;
import com.agent.editor.service.SessionMemoryQueryService;
import com.agent.editor.service.TaskApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentControllerTest {

    @Test
    void shouldExecuteTaskThroughTaskApplicationService() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        SessionMemoryQueryService sessionMemoryQueryService = mock(SessionMemoryQueryService.class);
        AgentController controller = new AgentController(taskApplicationService, sessionMemoryQueryService);

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-1");
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);

        AgentTaskResponse response = new AgentTaskResponse();
        response.setTaskId("task-1");
        response.setStatus("RUNNING");

        when(taskApplicationService.executeAsync(request)).thenReturn(response);

        ResponseEntity<AgentTaskResponse> result = controller.executeAgentTask(request);

        assertEquals(202, result.getStatusCode().value());
        assertSame(response, result.getBody());
        verify(taskApplicationService).executeAsync(request);
    }

    @Test
    void shouldReadTaskStatusThroughTaskApplicationService() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        SessionMemoryQueryService sessionMemoryQueryService = mock(SessionMemoryQueryService.class);
        AgentController controller = new AgentController(taskApplicationService, sessionMemoryQueryService);

        AgentTaskResponse response = new AgentTaskResponse();
        response.setTaskId("task-1");
        response.setStatus("COMPLETED");

        when(taskApplicationService.getTaskStatus("task-1")).thenReturn(response);

        ResponseEntity<AgentTaskResponse> result = controller.getTaskStatus("task-1");

        assertEquals(200, result.getStatusCode().value());
        assertSame(response, result.getBody());
        verify(taskApplicationService).getTaskStatus("task-1");
    }

    @Test
    void shouldExposeSupervisorMode() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        SessionMemoryQueryService sessionMemoryQueryService = mock(SessionMemoryQueryService.class);
        AgentController controller = new AgentController(taskApplicationService, sessionMemoryQueryService);

        ResponseEntity<java.util.List<String>> result = controller.getSupportedModes();

        assertEquals(200, result.getStatusCode().value());
        assertTrue(result.getBody().contains(AgentMode.SUPERVISOR.name()));
        assertTrue(result.getBody().contains(AgentMode.REFLEXION.name()));
    }

    @Test
    void shouldReadSessionMemoryThroughQueryService() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        SessionMemoryQueryService sessionMemoryQueryService = mock(SessionMemoryQueryService.class);
        AgentController controller = new AgentController(taskApplicationService, sessionMemoryQueryService);

        SessionMemoryResponse response = new SessionMemoryResponse();
        response.setSessionId("session-1");
        response.setMessageCount(1);

        when(sessionMemoryQueryService.getSessionMemory("session-1")).thenReturn(response);

        ResponseEntity<SessionMemoryResponse> result = controller.getSessionMemory("session-1");

        assertEquals(200, result.getStatusCode().value());
        assertSame(response, result.getBody());
        verify(sessionMemoryQueryService).getSessionMemory("session-1");
    }

    @Test
    void shouldExposeNativeExecutionEvents() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        SessionMemoryQueryService sessionMemoryQueryService = mock(SessionMemoryQueryService.class);
        AgentController controller = new AgentController(taskApplicationService, sessionMemoryQueryService);
        List<ExecutionEvent> events = List.of(new ExecutionEvent(EventType.TOOL_CALLED, "task-1", "editDocument"));

        when(taskApplicationService.getTaskEvents("task-1")).thenReturn(events);

        ResponseEntity<List<ExecutionEvent>> result = controller.getTaskEvents("task-1");

        assertEquals(200, result.getStatusCode().value());
        assertSame(events, result.getBody());
        verify(taskApplicationService).getTaskEvents("task-1");
    }

    @Test
    void shouldReturnServiceUnavailableWhenTaskSubmissionRejected() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        SessionMemoryQueryService sessionMemoryQueryService = mock(SessionMemoryQueryService.class);
        AgentController controller = new AgentController(taskApplicationService, sessionMemoryQueryService);

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-1");
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);

        when(taskApplicationService.executeAsync(request)).thenThrow(new IllegalStateException("Failed to submit agent task"));

        ResponseEntity<AgentTaskResponse> result = controller.executeAgentTask(request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.getStatusCode());
        assertNull(result.getBody());
    }

    @Test
    void shouldUseFinalAgentRoutePrefix() {
        RequestMapping mapping = AgentController.class.getAnnotation(RequestMapping.class);

        assertEquals("/api/agent", mapping.value()[0]);
    }

    @Test
    void shouldNotExposeLegacyConnectEndpoint() {
        boolean hasLegacyConnectEndpoint = java.util.Arrays.stream(AgentController.class.getDeclaredMethods())
                .anyMatch(method -> {
                    PostMapping mapping = method.getAnnotation(PostMapping.class);
                    return mapping != null && mapping.value().length > 0 && "/connect".equals(mapping.value()[0]);
                });

        assertFalse(hasLegacyConnectEndpoint);
    }
}
