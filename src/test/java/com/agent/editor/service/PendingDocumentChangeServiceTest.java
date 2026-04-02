package com.agent.editor.service;

import com.agent.editor.dto.PendingDocumentChange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PendingDocumentChangeServiceTest {

    @Test
    void shouldSaveAndReadPendingChangeByDocumentId() {
        PendingDocumentChangeService service = new PendingDocumentChangeService(new DiffService());

        PendingDocumentChange pendingChange = service.savePendingChange("doc-1", "task-1", "old", "new");

        assertNotNull(pendingChange);
        assertEquals("doc-1", pendingChange.getDocumentId());
        assertEquals("task-1", pendingChange.getTaskId());
        assertEquals("new", service.getPendingChange("doc-1").getProposedContent());
    }

    @Test
    void shouldReplacePreviousPendingChangeForSameDocument() {
        PendingDocumentChangeService service = new PendingDocumentChangeService(new DiffService());

        service.savePendingChange("doc-1", "task-1", "old", "draft one");
        PendingDocumentChange latest = service.savePendingChange("doc-1", "task-2", "old", "draft two");

        assertEquals("task-2", latest.getTaskId());
        assertEquals("draft two", service.getPendingChange("doc-1").getProposedContent());
    }

    @Test
    void shouldDiscardPendingChange() {
        PendingDocumentChangeService service = new PendingDocumentChangeService(new DiffService());

        service.savePendingChange("doc-1", "task-1", "old", "new");
        service.discardPendingChange("doc-1");

        assertNull(service.getPendingChange("doc-1"));
    }
}
