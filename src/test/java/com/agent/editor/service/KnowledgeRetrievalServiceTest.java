package com.agent.editor.service;

import com.agent.editor.config.RagProperties;
import com.agent.editor.repository.KnowledgeChunkRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeRetrievalServiceTest {

    @Test
    void shouldHonorExplicitTopKWhenRetrievingHybridResults() {
        KnowledgeChunkRepository repository = mock(KnowledgeChunkRepository.class);
        KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
        when(embeddingService.embed("Spring Boot")).thenReturn(new float[]{0.1f, 0.2f});
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(
                repository,
                embeddingService,
                com.agent.editor.testsupport.ConfigurationTestFixtures.ragProperties(500, 80, 5, 8, 12)
        );

        service.retrieve("Spring Boot", List.of("doc-1"), 2);

        verify(embeddingService).embed("Spring Boot");
        verify(repository).searchHybrid(eq("Spring Boot"), any(), eq(List.of("doc-1")), eq(2));
    }

    @Test
    void shouldReturnEmptyResultsForBlankQuery() {
        KnowledgeChunkRepository repository = mock(KnowledgeChunkRepository.class);
        KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(
                repository,
                embeddingService,
                com.agent.editor.testsupport.ConfigurationTestFixtures.ragProperties(500, 80, 5, 8, 12)
        );

        assertTrue(service.retrieve("   ", null, null).isEmpty());
        verify(embeddingService, never()).embed("   ");
    }
}
