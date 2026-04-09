package com.agent.editor.agent.core.memory;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class LongTermMemoryItemTest {

    @Test
    void shouldDefensivelyCopyTagsWhenConstructed() {
        List<String> tags = new ArrayList<>(List.of("profile", "style"));

        LongTermMemoryItem item = new LongTermMemoryItem(
                "memory-1",
                LongTermMemoryType.USER_PROFILE,
                null,
                "Prefer concise Chinese answers",
                "User explicitly asked for concise Chinese answers",
                "task-1",
                "session-1",
                tags,
                LocalDateTime.of(2026, 4, 3, 10, 0),
                LocalDateTime.of(2026, 4, 3, 10, 5),
                new float[]{0.1f, 0.2f}
        );

        tags.add("mutated");

        assertEquals(List.of("profile", "style"), item.getTags());
        assertNotSame(tags, item.getTags());
    }

    @Test
    void shouldCarryTypeAndSummaryForUserProfileMemory() {
        LongTermMemoryItem item = new LongTermMemoryItem();
        item.setMemoryType(LongTermMemoryType.USER_PROFILE);
        item.setSummary("Always answer in Chinese unless asked otherwise");

        assertEquals(LongTermMemoryType.USER_PROFILE, item.getMemoryType());
        assertEquals("Always answer in Chinese unless asked otherwise", item.getSummary());
    }

    @Test
    void shouldCarryDocumentAndSourceMetadataForDocumentDecisionMemory() {
        LongTermMemoryItem item = new LongTermMemoryItem();
        item.setMemoryType(LongTermMemoryType.DOCUMENT_DECISION);
        item.setDocumentId("doc-123");
        item.setSourceTaskId("task-9");
        item.setSourceSessionId("session-4");
        item.setSummary("Keep section 3 structure unchanged");

        assertEquals(LongTermMemoryType.DOCUMENT_DECISION, item.getMemoryType());
        assertEquals("doc-123", item.getDocumentId());
        assertEquals("task-9", item.getSourceTaskId());
        assertEquals("session-4", item.getSourceSessionId());
        assertEquals("Keep section 3 structure unchanged", item.getSummary());
    }
}
