package com.agent.editor.controller;

import com.agent.editor.dto.DiffResult;
import com.agent.editor.dto.PendingDocumentChange;
import com.agent.editor.service.DiffService;
import com.agent.editor.service.TaskApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiffControllerTest {

    @Test
    void shouldReturnPendingDocumentChange() {
        DiffService diffService = mock(DiffService.class);
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        DiffController controller = new DiffController(diffService, taskApplicationService);
        PendingDocumentChange pendingChange = new PendingDocumentChange(
                "doc-1",
                "task-1",
                "old",
                "new",
                "<div>diff</div>",
                LocalDateTime.now()
        );

        when(taskApplicationService.getPendingDocumentChange("doc-1")).thenReturn(pendingChange);

        ResponseEntity<PendingDocumentChange> result = controller.getPendingChange("doc-1");

        assertEquals(200, result.getStatusCode().value());
        assertSame(pendingChange, result.getBody());
        verify(taskApplicationService).getPendingDocumentChange("doc-1");
    }

    @Test
    void shouldReturnNotFoundWhenPendingDocumentChangeDoesNotExist() {
        DiffService diffService = mock(DiffService.class);
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        DiffController controller = new DiffController(diffService, taskApplicationService);

        when(taskApplicationService.getPendingDocumentChange("doc-1")).thenReturn(null);

        ResponseEntity<PendingDocumentChange> result = controller.getPendingChange("doc-1");

        assertEquals(404, result.getStatusCode().value());
        assertNull(result.getBody());
    }

    @Test
    void shouldApplyPendingDocumentChangeThroughTaskApplicationService() {
        DiffService diffService = mock(DiffService.class);
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        DiffController controller = new DiffController(diffService, taskApplicationService);
        PendingDocumentChange pendingChange = new PendingDocumentChange(
                "doc-1",
                "task-1",
                "old",
                "new",
                "<div>diff</div>",
                LocalDateTime.now()
        );

        when(taskApplicationService.applyPendingDocumentChange("doc-1")).thenReturn(pendingChange);

        ResponseEntity<PendingDocumentChange> result = controller.applyPendingChange("doc-1");

        assertEquals(200, result.getStatusCode().value());
        assertSame(pendingChange, result.getBody());
        verify(taskApplicationService).applyPendingDocumentChange("doc-1");
    }

    @Test
    void shouldDiscardPendingDocumentChangeThroughTaskApplicationService() {
        DiffService diffService = mock(DiffService.class);
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        DiffController controller = new DiffController(diffService, taskApplicationService);

        ResponseEntity<Void> result = controller.discardPendingChange("doc-1");

        assertEquals(204, result.getStatusCode().value());
        verify(taskApplicationService).discardPendingDocumentChange("doc-1");
    }

    @Test
    void shouldExposeAppliedDiffHistoryThroughDiffService() {
        DiffService diffService = mock(DiffService.class);
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        DiffController controller = new DiffController(diffService, taskApplicationService);
        List<DiffResult> history = List.of(new DiffResult("old", "new", "<div>diff</div>"));

        when(diffService.getDiffHistory("doc-1")).thenReturn(history);

        ResponseEntity<List<DiffResult>> result = controller.getDiffHistory("doc-1");

        assertEquals(200, result.getStatusCode().value());
        assertSame(history, result.getBody());
        verify(diffService).getDiffHistory("doc-1");
    }
}
