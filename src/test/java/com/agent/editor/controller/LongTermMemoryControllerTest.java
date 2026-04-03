package com.agent.editor.controller;

import com.agent.editor.dto.ConfirmLongTermMemoryRequest;
import com.agent.editor.dto.LongTermMemoryCandidateResponse;
import com.agent.editor.dto.PendingLongTermMemoryResponse;
import com.agent.editor.service.TaskApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LongTermMemoryControllerTest {

    @Test
    void shouldListPendingCandidatesByTaskId() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        LongTermMemoryController controller = new LongTermMemoryController(taskApplicationService);
        PendingLongTermMemoryResponse response = response("task-1", "candidate-1");

        when(taskApplicationService.getPendingLongTermMemoryCandidates("task-1")).thenReturn(response);

        ResponseEntity<PendingLongTermMemoryResponse> result = controller.getPendingCandidates("task-1");

        assertEquals(200, result.getStatusCode().value());
        assertSame(response, result.getBody());
    }

    @Test
    void shouldConfirmSelectedCandidates() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        LongTermMemoryController controller = new LongTermMemoryController(taskApplicationService);
        PendingLongTermMemoryResponse response = response("task-1", "candidate-1");
        ConfirmLongTermMemoryRequest request = new ConfirmLongTermMemoryRequest();
        request.setCandidateIds(List.of("candidate-1"));

        when(taskApplicationService.confirmLongTermMemoryCandidates("task-1", List.of("candidate-1"))).thenReturn(response);

        ResponseEntity<PendingLongTermMemoryResponse> result = controller.confirmCandidates("task-1", request);

        assertEquals(200, result.getStatusCode().value());
        assertSame(response, result.getBody());
        verify(taskApplicationService).confirmLongTermMemoryCandidates("task-1", List.of("candidate-1"));
    }

    @Test
    void shouldDiscardSelectedCandidates() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        LongTermMemoryController controller = new LongTermMemoryController(taskApplicationService);
        PendingLongTermMemoryResponse response = response("task-1", "candidate-2");
        ConfirmLongTermMemoryRequest request = new ConfirmLongTermMemoryRequest();
        request.setCandidateIds(List.of("candidate-2"));

        when(taskApplicationService.discardLongTermMemoryCandidates("task-1", List.of("candidate-2"))).thenReturn(response);

        ResponseEntity<PendingLongTermMemoryResponse> result = controller.discardCandidates("task-1", request);

        assertEquals(200, result.getStatusCode().value());
        assertSame(response, result.getBody());
        verify(taskApplicationService).discardLongTermMemoryCandidates("task-1", List.of("candidate-2"));
    }

    @Test
    void shouldReturnNotFoundWhenNoPendingCandidatesExist() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        LongTermMemoryController controller = new LongTermMemoryController(taskApplicationService);

        when(taskApplicationService.getPendingLongTermMemoryCandidates("task-1")).thenReturn(null);

        ResponseEntity<PendingLongTermMemoryResponse> result = controller.getPendingCandidates("task-1");

        assertEquals(404, result.getStatusCode().value());
        assertNull(result.getBody());
    }

    private PendingLongTermMemoryResponse response(String taskId, String candidateId) {
        PendingLongTermMemoryResponse response = new PendingLongTermMemoryResponse();
        response.setTaskId(taskId);
        response.setCandidates(List.of(new LongTermMemoryCandidateResponse(
                candidateId,
                "DOCUMENT_DECISION",
                "Keep section 3 unchanged",
                "doc-1"
        )));
        return response;
    }
}
