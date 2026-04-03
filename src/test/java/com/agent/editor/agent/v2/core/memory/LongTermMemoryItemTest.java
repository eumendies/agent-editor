package com.agent.editor.agent.v2.core.memory;

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
                LongTermMemoryScopeType.PROFILE,
                "default",
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
    void shouldSupportDefaultProfileScopeForUserProfileMemory() {
        LongTermMemoryItem item = new LongTermMemoryItem();
        item.setMemoryType(LongTermMemoryType.USER_PROFILE);
        item.setScopeType(LongTermMemoryScopeType.PROFILE);
        item.setScopeKey("default");
        item.setSummary("Always answer in Chinese unless asked otherwise");

        assertEquals(LongTermMemoryType.USER_PROFILE, item.getMemoryType());
        assertEquals(LongTermMemoryScopeType.PROFILE, item.getScopeType());
        assertEquals("default", item.getScopeKey());
        assertEquals("Always answer in Chinese unless asked otherwise", item.getSummary());
    }

    @Test
    void shouldCarryDocumentAndSourceMetadataForTaskDecisionMemory() {
        LongTermMemoryItem item = new LongTermMemoryItem();
        item.setMemoryType(LongTermMemoryType.TASK_DECISION);
        item.setScopeType(LongTermMemoryScopeType.DOCUMENT);
        item.setScopeKey("doc-123");
        item.setDocumentId("doc-123");
        item.setSourceTaskId("task-9");
        item.setSourceSessionId("session-4");
        item.setSummary("Keep section 3 structure unchanged");

        assertEquals(LongTermMemoryType.TASK_DECISION, item.getMemoryType());
        assertEquals(LongTermMemoryScopeType.DOCUMENT, item.getScopeType());
        assertEquals("doc-123", item.getDocumentId());
        assertEquals("task-9", item.getSourceTaskId());
        assertEquals("session-4", item.getSourceSessionId());
        assertEquals("Keep section 3 structure unchanged", item.getSummary());
    }
}
