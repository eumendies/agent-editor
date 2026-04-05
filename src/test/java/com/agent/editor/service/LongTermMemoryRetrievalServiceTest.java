package com.agent.editor.service;

import com.agent.editor.agent.v2.core.memory.LongTermMemoryItem;
import com.agent.editor.agent.v2.core.memory.LongTermMemoryType;
import com.agent.editor.model.RetrievedLongTermMemory;
import com.agent.editor.repository.LongTermMemoryRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LongTermMemoryRetrievalServiceTest {

    @Test
    void shouldLoadConfirmedProfilesByDefaultScope() {
        LongTermMemoryRepository repository = mock(LongTermMemoryRepository.class);
        KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
        when(repository.listUserProfiles())
                .thenReturn(List.of(memory(
                        "memory-1",
                        LongTermMemoryType.USER_PROFILE,
                        null,
                        "Always answer in Chinese"
                )));
        LongTermMemoryRetrievalService service = new LongTermMemoryRetrievalService(repository, embeddingService);

        List<LongTermMemoryItem> results = service.loadConfirmedProfiles();

        assertEquals(1, results.size());
        assertEquals("Always answer in Chinese", results.get(0).getSummary());
        verify(repository).listUserProfiles();
        verify(embeddingService, never()).embed(any());
    }

    @Test
    void shouldSearchConfirmedDocumentDecisionsWithDocumentScopeAndEmbedding() {
        LongTermMemoryRepository repository = mock(LongTermMemoryRepository.class);
        KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
        when(embeddingService.embed("continue previous editing choices")).thenReturn(new float[]{0.1f, 0.2f});
        when(repository.searchConfirmedDocumentDecisions("doc-1", new float[]{0.1f, 0.2f}, 2))
                .thenReturn(List.of(memory(
                        "memory-2",
                        LongTermMemoryType.DOCUMENT_DECISION,
                        "doc-1",
                        "Keep section 3 unchanged"
                )));
        LongTermMemoryRetrievalService service = new LongTermMemoryRetrievalService(repository, embeddingService);

        List<RetrievedLongTermMemory> results = service.searchConfirmedDocumentDecisions(
                "continue previous editing choices",
                "doc-1",
                2
        );

        assertEquals(1, results.size());
        assertEquals("memory-2", results.get(0).getMemoryId());
        assertEquals("Keep section 3 unchanged", results.get(0).getSummary());
        verify(embeddingService).embed("continue previous editing choices");
        verify(repository).searchConfirmedDocumentDecisions(eq("doc-1"), any(), eq(2));
    }

    @Test
    void shouldReturnEmptyResultsForBlankDecisionQuery() {
        LongTermMemoryRepository repository = mock(LongTermMemoryRepository.class);
        KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
        LongTermMemoryRetrievalService service = new LongTermMemoryRetrievalService(repository, embeddingService);

        assertTrue(service.searchConfirmedDocumentDecisions("  ", "doc-1", 3).isEmpty());
        verify(embeddingService, never()).embed(any());
    }

    @Test
    void shouldGracefullyReturnEmptyResultsWhenRepositoryIsUnavailable() {
        KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
        LongTermMemoryRetrievalService service = new LongTermMemoryRetrievalService(
                (LongTermMemoryRepository) null,
                embeddingService
        );

        assertTrue(service.loadConfirmedProfiles().isEmpty());
        assertTrue(service.searchConfirmedDocumentDecisions("continue previous editing choices", "doc-1", 3).isEmpty());
        verify(embeddingService, never()).embed(any());
    }

    private LongTermMemoryItem memory(String memoryId,
                                      LongTermMemoryType memoryType,
                                      String documentId,
                                      String summary) {
        return new LongTermMemoryItem(
                memoryId,
                memoryType,
                documentId,
                summary,
                summary + " details",
                "task-1",
                "session-1",
                List.of("confirmed"),
                LocalDateTime.of(2026, 4, 3, 10, 0),
                LocalDateTime.of(2026, 4, 3, 10, 5),
                new float[]{0.1f, 0.2f}
        );
    }
}
