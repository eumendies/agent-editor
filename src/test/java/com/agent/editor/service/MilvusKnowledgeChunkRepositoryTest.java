package com.agent.editor.service;

import com.agent.editor.config.MilvusProperties;
import com.agent.editor.model.KnowledgeChunk;
import com.agent.editor.model.RetrievedKnowledgeChunk;
import com.agent.editor.repository.MilvusKnowledgeChunkRepository;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.HybridSearchReq;
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
        when(milvusClient.hybridSearch(any(HybridSearchReq.class))).thenReturn(SearchResp.builder()
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
                new MilvusProperties("localhost", 19530, "knowledge_chunks_v2", 3)
        );

        List<RetrievedKnowledgeChunk> results = repository.searchHybrid(
                "Spring Boot",
                new float[]{0.1f, 0.2f, 0.3f},
                null,
                3
        );

        assertEquals(1, results.size());
        assertEquals("resume.md", results.get(0).getFileName());
        assertEquals("项目经历", results.get(0).getHeading());
        assertEquals("Spring Boot 项目经验", results.get(0).getChunkText());
        assertTrue(results.get(0).getScore() > 0);

        ArgumentCaptor<HybridSearchReq> requestCaptor = ArgumentCaptor.forClass(HybridSearchReq.class);
        verify(milvusClient).hybridSearch(requestCaptor.capture());
        assertEquals("knowledge_chunks_v2", requestCaptor.getValue().getCollectionName());
        assertEquals(3, requestCaptor.getValue().getTopK());
        assertEquals(2, requestCaptor.getValue().getSearchRequests().size());
        assertEquals("embedding", requestCaptor.getValue().getSearchRequests().get(0).getVectorFieldName());
        assertEquals("sparseFullText", requestCaptor.getValue().getSearchRequests().get(1).getVectorFieldName());
    }

    @Test
    void shouldFallbackToVectorSearchWhenHybridSearchFails() {
        MilvusClientV2 milvusClient = mock(MilvusClientV2.class);
        when(milvusClient.hybridSearch(any(HybridSearchReq.class))).thenThrow(new RuntimeException("hybrid failed"));
        when(milvusClient.search(any(SearchReq.class))).thenReturn(SearchResp.builder()
                .searchResults(List.of(List.of()))
                .build());
        MilvusKnowledgeChunkRepository repository = new MilvusKnowledgeChunkRepository(
                milvusClient,
                new MilvusProperties("localhost", 19530, "knowledge_chunks_v2", 3)
        );

        List<RetrievedKnowledgeChunk> results = repository.searchHybrid(
                "Spring Boot",
                new float[]{0.1f, 0.2f, 0.3f},
                List.of("doc-1"),
                3
        );

        assertTrue(results.isEmpty());

        ArgumentCaptor<SearchReq> requestCaptor = ArgumentCaptor.forClass(SearchReq.class);
        verify(milvusClient).search(requestCaptor.capture());
        assertEquals("knowledge_chunks_v2", requestCaptor.getValue().getCollectionName());
        assertEquals("documentId in [\"doc-1\"]", requestCaptor.getValue().getFilter());
    }

    @Test
    void shouldApplyConfiguredHybridRangeFilters() {
        MilvusClientV2 milvusClient = mock(MilvusClientV2.class);
        when(milvusClient.hybridSearch(any(HybridSearchReq.class))).thenReturn(SearchResp.builder()
                .searchResults(List.of(List.of()))
                .build());
        MilvusProperties properties = new MilvusProperties("localhost", 19530, "knowledge_chunks_v2", 3);
        MilvusProperties.HybridProperties hybrid = new MilvusProperties.HybridProperties();
        hybrid.setDense(new MilvusProperties.RangeSearchProperties(0.82d, 1.0d));
        hybrid.setSparse(new MilvusProperties.RangeSearchProperties(0.15d, 1.0d));
        properties.setHybrid(hybrid);
        MilvusKnowledgeChunkRepository repository = new MilvusKnowledgeChunkRepository(milvusClient, properties);

        repository.searchHybrid(
                "Spring Boot",
                new float[]{0.1f, 0.2f, 0.3f},
                null,
                3
        );

        ArgumentCaptor<HybridSearchReq> requestCaptor = ArgumentCaptor.forClass(HybridSearchReq.class);
        verify(milvusClient).hybridSearch(requestCaptor.capture());

        HybridSearchReq request = requestCaptor.getValue();
        assertEquals("{\"radius\":0.82,\"range_filter\":1.0}", request.getSearchRequests().get(0).getParams());
        assertEquals("{\"radius\":0.15,\"range_filter\":1.0}", request.getSearchRequests().get(1).getParams());
    }

    @Test
    void shouldUpsertChunkEmbeddingPayloads() {
        MilvusClientV2 milvusClient = mock(MilvusClientV2.class);
        MilvusKnowledgeChunkRepository repository = new MilvusKnowledgeChunkRepository(
                milvusClient,
                new MilvusProperties("localhost", 19530, "knowledge_chunks_v2", 3)
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

        assertEquals("knowledge_chunks_v2", request.getCollectionName());
        assertEquals("doc-1", request.getData().get(0).get("documentId").getAsString());
        assertEquals("resume.md", request.getData().get(0).get("fileName").getAsString());
        assertEquals("项目经历", request.getData().get(0).get("heading").getAsString());
        assertEquals("Spring Boot 项目经验", request.getData().get(0).get("chunkText").getAsString());
        assertEquals("项目经历\nSpring Boot 项目经验", request.getData().get(0).get("fullText").getAsString());
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
