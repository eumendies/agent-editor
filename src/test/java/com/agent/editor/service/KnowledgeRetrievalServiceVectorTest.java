package com.agent.editor.service;

import com.agent.editor.config.RagProperties;
import com.agent.editor.model.RetrievedKnowledgeChunk;
import com.agent.editor.repository.KnowledgeChunkRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeRetrievalServiceVectorTest {

    @Test
    void shouldRetrieveByQueryEmbedding() {
        KnowledgeChunkRepository repository = mock(KnowledgeChunkRepository.class);
        KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
        when(embeddingService.embed("streaming project")).thenReturn(new float[]{0.1f, 0.2f});
        when(repository.searchHybrid(eq("streaming project"), any(), isNull(), eq(5)))
                .thenReturn(List.of(new RetrievedKnowledgeChunk(
                        "doc-1",
                        "resume.md",
                        0,
                        "项目经历",
                        "流式处理项目",
                        0.92
                )));
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(
                repository,
                embeddingService,
                new RagProperties(500, 80, 5, 8, 12)
        );

        List<RetrievedKnowledgeChunk> results = service.retrieve("streaming project", null, null);

        assertEquals(1, results.size());
        assertEquals(0.92, results.get(0).score());
        verify(embeddingService).embed("streaming project");
        verify(repository).searchHybrid(eq("streaming project"), any(), isNull(), eq(5));
    }
}
