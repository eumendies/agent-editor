package com.agent.editor.controller;

import com.agent.editor.dto.AgentTaskRequest;
import com.agent.editor.dto.AgentTaskResponse;
import com.agent.editor.model.AgentMode;
import com.agent.editor.service.DocumentService;
import com.agent.editor.service.TaskApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentControllerTest {

    @Test
    void shouldExecuteTaskThroughTaskApplicationService() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        DocumentService documentService = mock(DocumentService.class);
        AgentController controller = new AgentController(taskApplicationService, documentService);

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-1");
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);

        AgentTaskResponse response = new AgentTaskResponse();
        response.setTaskId("task-1");
        response.setStatus("COMPLETED");

        when(taskApplicationService.execute(request)).thenReturn(response);

        ResponseEntity<AgentTaskResponse> result = controller.executeAgentTask(request);

        assertEquals(200, result.getStatusCode().value());
        assertSame(response, result.getBody());
        verify(taskApplicationService).execute(request);
    }

    @Test
    void shouldReadTaskStatusThroughTaskApplicationService() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        DocumentService documentService = mock(DocumentService.class);
        AgentController controller = new AgentController(taskApplicationService, documentService);

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
        DocumentService documentService = mock(DocumentService.class);
        AgentController controller = new AgentController(taskApplicationService, documentService);

        ResponseEntity<java.util.List<String>> result = controller.getSupportedModes();

        assertEquals(200, result.getStatusCode().value());
        assertTrue(result.getBody().contains(AgentMode.SUPERVISOR.name()));
        assertTrue(result.getBody().contains(AgentMode.REFLEXION.name()));
    }
}
