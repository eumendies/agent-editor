package com.agent.editor.agent.v2.memory;

import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.PendingLongTermMemoryItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LongTermMemoryExtractorTest {

    @Test
    void shouldExtractUserProfileCandidatesFromAiServiceResponse() {
        LongTermMemoryExtractionAiService aiService = mock(LongTermMemoryExtractionAiService.class);
        LongTermMemoryExtractor extractor = new LongTermMemoryExtractor(aiService, null);
        when(aiService.extract(contains("USER: Always answer in Chinese from now on")))
                .thenReturn(response(List.of(summary("Always answer in Chinese")), List.of()));

        List<PendingLongTermMemoryItem> candidates = extractor.extractCandidates(
                "task-1",
                "session-1",
                "doc-1",
                new ChatTranscriptMemory(List.of(new ChatMessage.UserChatMessage("Always answer in Chinese from now on")))
        );

        assertEquals(1, candidates.size());
        assertEquals("USER_PROFILE", candidates.get(0).getMemoryType().name());
        assertTrue(candidates.get(0).getSummary().contains("Always answer in Chinese"));
    }

    @Test
    void shouldExtractDocumentDecisionCandidatesFromAiServiceResponse() {
        LongTermMemoryExtractionAiService aiService = mock(LongTermMemoryExtractionAiService.class);
        LongTermMemoryExtractor extractor = new LongTermMemoryExtractor(aiService, null);
        when(aiService.extract(contains("USER: Keep section 3 unchanged and only revise section 4")))
                .thenReturn(response(List.of(), List.of(summary("Keep section 3 unchanged"))));

        List<PendingLongTermMemoryItem> candidates = extractor.extractCandidates(
                "task-2",
                "session-2",
                "doc-9",
                new ChatTranscriptMemory(List.of(new ChatMessage.UserChatMessage("Keep section 3 unchanged and only revise section 4")))
        );

        assertEquals(1, candidates.size());
        assertEquals("DOCUMENT_DECISION", candidates.get(0).getMemoryType().name());
        assertEquals("doc-9", candidates.get(0).getDocumentId());
        assertTrue(candidates.get(0).getSummary().contains("Keep section 3 unchanged"));
    }

    @Test
    void shouldFilterBlankSummariesAndSkipDocumentDecisionsWithoutDocumentId() {
        LongTermMemoryExtractionAiService aiService = mock(LongTermMemoryExtractionAiService.class);
        LongTermMemoryExtractor extractor = new LongTermMemoryExtractor(aiService, null);
        when(aiService.extract(contains("USER: Rewrite the introduction")))
                .thenReturn(response(
                        List.of(summary("   ")),
                        List.of(summary("Do not change section 3"))
                ));

        List<PendingLongTermMemoryItem> candidates = extractor.extractCandidates(
                "task-3",
                "session-3",
                null,
                new ChatTranscriptMemory(List.of(new ChatMessage.UserChatMessage("Rewrite the introduction")))
        );

        assertTrue(candidates.isEmpty());
    }

    @Test
    void shouldRenderAssistantAndToolMessagesIntoTranscript() {
        LongTermMemoryExtractionAiService aiService = mock(LongTermMemoryExtractionAiService.class);
        LongTermMemoryExtractor extractor = new LongTermMemoryExtractor(aiService, null);
        when(aiService.extract(contains("TOOL[retrieve_knowledge]: evidence found")))
                .thenReturn(response(List.of(), List.of()));

        extractor.extractCandidates(
                "task-4",
                "session-4",
                "doc-4",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("Continue the previous direction"),
                        new ChatMessage.AiChatMessage("I will check prior evidence"),
                        new ChatMessage.ToolExecutionResultChatMessage("tool-1", "retrieve_knowledge", "{}", "evidence found")
                ))
        );

        verify(aiService).extract(contains("ASSISTANT: I will check prior evidence"));
        verify(aiService).extract(contains("TOOL[retrieve_knowledge]: evidence found"));
    }

    private LongTermMemoryExtractionResponse response(List<MemoryCandidateSummary> userProfiles,
                                                      List<MemoryCandidateSummary> documentDecisions) {
        LongTermMemoryExtractionResponse response = new LongTermMemoryExtractionResponse();
        response.setUserProfiles(userProfiles);
        response.setDocumentDecisions(documentDecisions);
        return response;
    }

    private MemoryCandidateSummary summary(String value) {
        MemoryCandidateSummary summary = new MemoryCandidateSummary();
        summary.setSummary(value);
        return summary;
    }
}
