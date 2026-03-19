package com.agent.editor.service;

import com.agent.editor.config.RagProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeRetrievalServiceTest {

    @Test
    void shouldHonorExplicitTopKWhenRetrievingVectors() {
        KnowledgeChunkRepository repository = mock(KnowledgeChunkRepository.class);
        KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
        when(embeddingService.embed("Spring Boot")).thenReturn(new float[]{0.1f, 0.2f});
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(
                repository,
                embeddingService,
                new RagProperties(500, 80, 5, 8, 12)
        );

        service.retrieve("Spring Boot", List.of("doc-1"), 2);

        verify(embeddingService).embed("Spring Boot");
        verify(repository).searchByVector(any(), eq(List.of("doc-1")), eq(2));
    }
}
