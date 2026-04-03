package com.agent.editor.service;

import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.PendingLongTermMemoryItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongTermMemoryExtractorTest {

    @Test
    void shouldExtractUserProfileCandidateOnlyForStablePreferencePhrasing() {
        LongTermMemoryExtractor extractor = new LongTermMemoryExtractor();

        List<PendingLongTermMemoryItem> candidates = extractor.extractCandidates(
                "task-1",
                "session-1",
                "doc-1",
                "Always answer in Chinese from now on",
                new ChatTranscriptMemory(List.of()),
                "done"
        );

        assertEquals(1, candidates.size());
        assertEquals("USER_PROFILE", candidates.get(0).getMemoryType().name());
        assertTrue(candidates.get(0).getSummary().contains("Always answer in Chinese"));
    }

    @Test
    void shouldExtractTaskDecisionCandidateFromExplicitKeepInstruction() {
        LongTermMemoryExtractor extractor = new LongTermMemoryExtractor();

        List<PendingLongTermMemoryItem> candidates = extractor.extractCandidates(
                "task-2",
                "session-2",
                "doc-9",
                "Keep section 3 unchanged and only revise section 4",
                new ChatTranscriptMemory(List.of()),
                "done"
        );

        assertEquals(1, candidates.size());
        assertEquals("TASK_DECISION", candidates.get(0).getMemoryType().name());
        assertEquals("doc-9", candidates.get(0).getDocumentId());
        assertTrue(candidates.get(0).getSummary().contains("Keep section 3 unchanged"));
    }

    @Test
    void shouldIgnoreRoutineEditingInstructions() {
        LongTermMemoryExtractor extractor = new LongTermMemoryExtractor();

        List<PendingLongTermMemoryItem> candidates = extractor.extractCandidates(
                "task-3",
                "session-3",
                "doc-3",
                "Rewrite the introduction",
                new ChatTranscriptMemory(List.of()),
                "done"
        );

        assertTrue(candidates.isEmpty());
    }
}
