package com.agent.editor.controller;

import com.agent.editor.agent.event.EventType;
import com.agent.editor.agent.event.ExecutionEvent;
import com.agent.editor.dto.AgentTaskRequest;
import com.agent.editor.dto.AgentTaskResponse;
import com.agent.editor.model.AgentMode;
import com.agent.editor.service.TaskApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentV2ControllerTest {

    @Test
    void shouldExecuteTaskThroughTaskApplicationService() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        AgentV2Controller controller = new AgentV2Controller(taskApplicationService);

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-1");
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);

        AgentTaskResponse response = new AgentTaskResponse();
        response.setTaskId("task-1");
        response.setStatus("RUNNING");

        when(taskApplicationService.executeV2(request)).thenReturn(response);

        ResponseEntity<AgentTaskResponse> result = controller.executeAgentTask(request);

        assertEquals(202, result.getStatusCode().value());
        assertSame(response, result.getBody());
        verify(taskApplicationService).executeV2(request);
    }

    @Test
    void shouldReadTaskStatusThroughTaskApplicationService() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        AgentV2Controller controller = new AgentV2Controller(taskApplicationService);

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
    void shouldExposeNativeExecutionEvents() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        AgentV2Controller controller = new AgentV2Controller(taskApplicationService);
        List<ExecutionEvent> events = List.of(new ExecutionEvent(EventType.TOOL_CALLED, "task-1", "editDocument"));

        when(taskApplicationService.getTaskEvents("task-1")).thenReturn(events);

        ResponseEntity<List<ExecutionEvent>> result = controller.getTaskEvents("task-1");

        assertEquals(200, result.getStatusCode().value());
        assertSame(events, result.getBody());
        assertEquals(EventType.TOOL_CALLED, result.getBody().get(0).getType());
        assertEquals("editDocument", result.getBody().get(0).getMessage());
        verify(taskApplicationService).getTaskEvents("task-1");
    }

    @Test
    void shouldReturnServiceUnavailableWhenTaskSubmissionRejected() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        AgentV2Controller controller = new AgentV2Controller(taskApplicationService);

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-1");
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);

        when(taskApplicationService.executeV2(request)).thenThrow(new IllegalStateException("Failed to submit agent task"));

        ResponseEntity<AgentTaskResponse> result = controller.executeAgentTask(request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.getStatusCode());
        assertNull(result.getBody());
    }
}
