package com.agent.editor.service;

import com.agent.editor.agent.v2.core.memory.LongTermMemoryItem;
import com.agent.editor.agent.v2.core.memory.LongTermMemoryType;
import com.agent.editor.agent.v2.core.memory.PendingLongTermMemoryItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PendingLongTermMemoryServiceTest {

    @Test
    void shouldSaveAndReadPendingCandidatesByTaskId() {
        PendingLongTermMemoryService service = new PendingLongTermMemoryService();

        service.savePendingCandidates("task-1", List.of(candidate("candidate-1", "summary-1")));

        assertEquals(1, service.getPendingCandidates("task-1").size());
        assertEquals("candidate-1", service.getPendingCandidates("task-1").get(0).getCandidateId());
    }

    @Test
    void shouldReplacePreviousPendingCandidatesForSameTask() {
        PendingLongTermMemoryService service = new PendingLongTermMemoryService();

        service.savePendingCandidates("task-1", List.of(candidate("candidate-1", "summary-1")));
        service.savePendingCandidates("task-1", List.of(candidate("candidate-2", "summary-2")));

        assertEquals(1, service.getPendingCandidates("task-1").size());
        assertEquals("candidate-2", service.getPendingCandidates("task-1").get(0).getCandidateId());
    }

    @Test
    void shouldRemoveConfirmedCandidatesWithoutDroppingOthers() {
        PendingLongTermMemoryService service = new PendingLongTermMemoryService();

        service.savePendingCandidates("task-1", List.of(
                candidate("candidate-1", "summary-1"),
                candidate("candidate-2", "summary-2")
        ));

        List<PendingLongTermMemoryItem> confirmed = service.confirmCandidates("task-1", List.of("candidate-2"));

        assertEquals(1, confirmed.size());
        assertEquals("candidate-2", confirmed.get(0).getCandidateId());
        assertEquals(List.of("candidate-1"), service.getPendingCandidates("task-1").stream()
                .map(PendingLongTermMemoryItem::getCandidateId)
                .toList());
    }

    @Test
    void shouldDiscardPendingCandidatesForTaskWithoutAffectingOtherTasks() {
        PendingLongTermMemoryService service = new PendingLongTermMemoryService();

        service.savePendingCandidates("task-1", List.of(candidate("candidate-1", "summary-1")));
        service.savePendingCandidates("task-2", List.of(candidate("candidate-2", "summary-2")));

        service.discardPendingCandidates("task-1");

        assertNull(service.getPendingCandidates("task-1"));
        assertEquals(1, service.getPendingCandidates("task-2").size());
    }

    private PendingLongTermMemoryItem candidate(String candidateId, String summary) {
        LongTermMemoryItem memoryItem = new LongTermMemoryItem();
        memoryItem.setMemoryType(LongTermMemoryType.DOCUMENT_DECISION);
        memoryItem.setScopeKey("doc-1");
        memoryItem.setDocumentId("doc-1");
        memoryItem.setSummary(summary);
        memoryItem.setSourceTaskId("task-1");
        memoryItem.setSourceSessionId("session-1");
        return new PendingLongTermMemoryItem(candidateId, memoryItem);
    }
}
