package com.agent.editor.service;

import com.agent.editor.agent.v2.core.memory.LongTermMemoryItem;
import com.agent.editor.agent.v2.core.memory.LongTermMemoryType;
import com.agent.editor.agent.v2.tool.memory.MemoryUpsertAction;
import com.agent.editor.repository.LongTermMemoryRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LongTermMemoryWriteServiceTest {

    @Test
    void shouldCreateUserProfileWithoutDocumentId() {
        LongTermMemoryRepository repository = mock(LongTermMemoryRepository.class);
        KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
        when(embeddingService.embed("Default to Chinese")).thenReturn(new float[]{0.1f, 0.2f});
        when(repository.createMemory(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LongTermMemoryWriteService service = new LongTermMemoryWriteService(
                repository,
                embeddingService,
                () -> "memory-1"
        );

        LongTermMemoryItem result = service.upsert(
                MemoryUpsertAction.CREATE,
                LongTermMemoryType.USER_PROFILE,
                null,
                null,
                "Default to Chinese"
        );

        assertEquals("memory-1", result.getMemoryId());
        assertEquals(LongTermMemoryType.USER_PROFILE, result.getMemoryType());
        assertEquals("Default to Chinese", result.getSummary());
        verify(repository).createMemory(any());
    }

    @Test
    void shouldRejectDocumentDecisionCreateWithoutDocumentId() {
        LongTermMemoryRepository repository = mock(LongTermMemoryRepository.class);
        KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
        LongTermMemoryWriteService service = new LongTermMemoryWriteService(
                repository,
                embeddingService,
                () -> "memory-1"
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.upsert(
                MemoryUpsertAction.CREATE,
                LongTermMemoryType.DOCUMENT_DECISION,
                null,
                null,
                "Keep section 3 unchanged"
        ));

        assertEquals("documentId is required for document decisions", exception.getMessage());
    }

    @Test
    void shouldReplaceExistingMemoryUsingSameIdUpsert() {
        LongTermMemoryRepository repository = mock(LongTermMemoryRepository.class);
        KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
        when(embeddingService.embed("Prefer concise summaries")).thenReturn(new float[]{0.3f, 0.4f});
        when(repository.findById("memory-2")).thenReturn(Optional.of(existingMemory("memory-2")));
        when(repository.createMemory(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LongTermMemoryWriteService service = new LongTermMemoryWriteService(
                repository,
                embeddingService,
                () -> "memory-unused"
        );

        LongTermMemoryItem result = service.upsert(
                MemoryUpsertAction.REPLACE,
                LongTermMemoryType.USER_PROFILE,
                "memory-2",
                null,
                "Prefer concise summaries"
        );

        assertEquals("memory-2", result.getMemoryId());
        assertEquals("Prefer concise summaries", result.getSummary());
        verify(repository).findById("memory-2");
        verify(repository).createMemory(any());
        verify(repository, never()).deleteMemory("memory-2");
    }

    @Test
    void shouldRejectReplacingDocumentDecisionAcrossDocuments() {
        LongTermMemoryRepository repository = mock(LongTermMemoryRepository.class);
        KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
        when(repository.findById("memory-4")).thenReturn(Optional.of(existingDocumentDecision("memory-4", "doc-1")));
        LongTermMemoryWriteService service = new LongTermMemoryWriteService(
                repository,
                embeddingService,
                () -> "memory-unused"
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.upsert(
                MemoryUpsertAction.REPLACE,
                LongTermMemoryType.DOCUMENT_DECISION,
                "memory-4",
                "doc-2",
                "Rewrite section 3"
        ));

        assertTrue(exception.getMessage().contains("documentId"));
        verify(repository).findById("memory-4");
        verify(repository, never()).createMemory(any());
        verify(repository, never()).deleteMemory("memory-4");
    }

    @Test
    void shouldKeepExistingMemoryWhenReplaceEmbeddingFails() {
        LongTermMemoryRepository repository = mock(LongTermMemoryRepository.class);
        KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
        when(repository.findById("memory-5")).thenReturn(Optional.of(existingMemory("memory-5")));
        when(embeddingService.embed("Prefer bullet summaries")).thenThrow(new IllegalStateException("embedding failed"));
        LongTermMemoryWriteService service = new LongTermMemoryWriteService(
                repository,
                embeddingService,
                () -> "memory-unused"
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.upsert(
                MemoryUpsertAction.REPLACE,
                LongTermMemoryType.USER_PROFILE,
                "memory-5",
                null,
                "Prefer bullet summaries"
        ));

        assertEquals("embedding failed", exception.getMessage());
        verify(repository).findById("memory-5");
        verify(repository, never()).deleteMemory("memory-5");
        verify(repository, never()).createMemory(any());
    }

    @Test
    void shouldDeleteExistingMemory() {
        LongTermMemoryRepository repository = mock(LongTermMemoryRepository.class);
        KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
        when(repository.findById("memory-3")).thenReturn(Optional.of(existingMemory("memory-3")));
        LongTermMemoryWriteService service = new LongTermMemoryWriteService(
                repository,
                embeddingService,
                () -> "memory-unused"
        );

        LongTermMemoryItem deleted = service.upsert(
                MemoryUpsertAction.DELETE,
                LongTermMemoryType.USER_PROFILE,
                "memory-3",
                null,
                null
        );

        assertEquals("memory-3", deleted.getMemoryId());
        verify(repository).deleteMemory("memory-3");
    }

    private LongTermMemoryItem existingMemory(String memoryId) {
        return new LongTermMemoryItem(
                memoryId,
                LongTermMemoryType.USER_PROFILE,
                "default",
                null,
                "Old summary",
                "Old summary details",
                "task-1",
                "session-1",
                List.of(),
                LocalDateTime.of(2026, 4, 3, 10, 0),
                LocalDateTime.of(2026, 4, 3, 10, 5),
                new float[]{0.1f, 0.2f}
        );
    }

    private LongTermMemoryItem existingDocumentDecision(String memoryId, String documentId) {
        return new LongTermMemoryItem(
                memoryId,
                LongTermMemoryType.DOCUMENT_DECISION,
                documentId,
                documentId,
                "Keep section 3 unchanged",
                "Keep section 3 unchanged",
                "task-1",
                "session-1",
                List.of(),
                LocalDateTime.of(2026, 4, 3, 10, 0),
                LocalDateTime.of(2026, 4, 3, 10, 5),
                new float[]{0.1f, 0.2f}
        );
    }
}
