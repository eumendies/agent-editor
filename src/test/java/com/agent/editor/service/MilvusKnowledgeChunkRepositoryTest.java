package com.agent.editor.service;

import com.agent.editor.config.MilvusProperties;
import com.agent.editor.model.KnowledgeChunk;
import com.agent.editor.model.RetrievedKnowledgeChunk;
import com.agent.editor.repository.MilvusKnowledgeChunkRepository;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.SearchResp;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MilvusKnowledgeChunkRepositoryTest {

    @Test
    void shouldMapMilvusHitsToRetrievedKnowledgeChunks() {
        MilvusClientV2 milvusClient = mock(MilvusClientV2.class);
        when(milvusClient.search(any(SearchReq.class))).thenReturn(SearchResp.builder()
                .searchResults(List.of(List.of(SearchResp.SearchResult.builder()
                        .entity(Map.of(
                                "documentId", "doc-1",
                                "fileName", "resume.md",
                                "chunkIndex", 0L,
                                "heading", "项目经历",
                                "chunkText", "Spring Boot 项目经验"
                        ))
                        .score(0.93f)
                        .build())))
                .build());
        MilvusKnowledgeChunkRepository repository = new MilvusKnowledgeChunkRepository(
                milvusClient,
                new MilvusProperties("localhost", 19530, "knowledge_chunks", 3)
        );

        List<RetrievedKnowledgeChunk> results = repository.searchByVector(new float[]{0.1f, 0.2f, 0.3f}, null, 3);

        assertEquals(1, results.size());
        assertEquals("resume.md", results.get(0).fileName());
        assertEquals("项目经历", results.get(0).heading());
        assertEquals("Spring Boot 项目经验", results.get(0).chunkText());
        assertTrue(results.get(0).score() > 0);

        ArgumentCaptor<SearchReq> requestCaptor = ArgumentCaptor.forClass(SearchReq.class);
        verify(milvusClient).search(requestCaptor.capture());
        assertEquals("knowledge_chunks", requestCaptor.getValue().getCollectionName());
        assertEquals(3, requestCaptor.getValue().getTopK());
        assertNull(requestCaptor.getValue().getFilter());
    }

    @Test
    void shouldUpsertChunkEmbeddingPayloads() {
        MilvusClientV2 milvusClient = mock(MilvusClientV2.class);
        MilvusKnowledgeChunkRepository repository = new MilvusKnowledgeChunkRepository(
                milvusClient,
                new MilvusProperties("localhost", 19530, "knowledge_chunks", 3)
        );

        repository.saveAll(List.of(new KnowledgeChunk(
                "doc-1",
                0,
                "resume.md",
                "项目经历",
                "Spring Boot 项目经验",
                Map.of("category", "resume", "documentType", "markdown"),
                new float[]{0.1f, 0.2f, 0.3f}
        )));

        ArgumentCaptor<UpsertReq> requestCaptor = ArgumentCaptor.forClass(UpsertReq.class);
        verify(milvusClient).upsert(requestCaptor.capture());
        UpsertReq request = requestCaptor.getValue();

        assertEquals("knowledge_chunks", request.getCollectionName());
        assertEquals("doc-1", request.getData().get(0).get("documentId").getAsString());
        assertEquals("resume.md", request.getData().get(0).get("fileName").getAsString());
        assertEquals("项目经历", request.getData().get(0).get("heading").getAsString());
        assertEquals("Spring Boot 项目经验", request.getData().get(0).get("chunkText").getAsString());
        assertEquals("resume", request.getData().get(0).get("category").getAsString());
        assertEquals("markdown", request.getData().get(0).get("documentType").getAsString());
        assertArrayEquals(
                new float[]{0.1f, 0.2f, 0.3f},
                new float[]{
                        request.getData().get(0).getAsJsonArray("embedding").get(0).getAsFloat(),
                        request.getData().get(0).getAsJsonArray("embedding").get(1).getAsFloat(),
                        request.getData().get(0).getAsJsonArray("embedding").get(2).getAsFloat()
                }
        );
    }
}
