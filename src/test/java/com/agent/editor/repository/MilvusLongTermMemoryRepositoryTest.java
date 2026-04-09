package com.agent.editor.repository;

import com.agent.editor.agent.core.memory.LongTermMemoryItem;
import com.agent.editor.agent.core.memory.LongTermMemoryType;
import com.agent.editor.config.LongTermMemoryMilvusProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MilvusLongTermMemoryRepositoryTest {

    @Test
    void shouldUpsertConfirmedUserProfilePayloads() {
        MilvusClientV2 milvusClient = mock(MilvusClientV2.class);
        MilvusLongTermMemoryRepository repository = new MilvusLongTermMemoryRepository(
                milvusClient,
                new LongTermMemoryMilvusProperties("long_term_memory_v1", 3)
        );

        LongTermMemoryItem created = repository.createMemory(memory(
                "memory-1",
                LongTermMemoryType.USER_PROFILE,
                null,
                "Always answer in Chinese",
                "User explicitly prefers Chinese answers"
        ));

        ArgumentCaptor<UpsertReq> requestCaptor = ArgumentCaptor.forClass(UpsertReq.class);
        verify(milvusClient).upsert(requestCaptor.capture());
        UpsertReq request = requestCaptor.getValue();

        assertEquals("memory-1", created.getMemoryId());
        assertEquals("long_term_memory_v1", request.getCollectionName());
        assertEquals("memory-1", request.getData().get(0).get("memoryId").getAsString());
        assertEquals("USER_PROFILE", request.getData().get(0).get("memoryType").getAsString());
        assertEquals("Always answer in Chinese", request.getData().get(0).get("summary").getAsString());
        assertTrue(request.getData().get(0).get("documentId").isJsonNull());
    }

    @Test
    void shouldUpsertConfirmedTaskDecisionPayloads() {
        MilvusClientV2 milvusClient = mock(MilvusClientV2.class);
        MilvusLongTermMemoryRepository repository = new MilvusLongTermMemoryRepository(
                milvusClient,
                new LongTermMemoryMilvusProperties("long_term_memory_v1", 3)
        );

        repository.createMemory(memory(
                "memory-2",
                LongTermMemoryType.DOCUMENT_DECISION,
                "doc-1",
                "Keep section 3 unchanged",
                "User accepted keeping section 3 structure unchanged"
        ));

        ArgumentCaptor<UpsertReq> requestCaptor = ArgumentCaptor.forClass(UpsertReq.class);
        verify(milvusClient).upsert(requestCaptor.capture());
        UpsertReq request = requestCaptor.getValue();

        assertEquals("DOCUMENT_DECISION", request.getData().get(0).get("memoryType").getAsString());
        assertEquals("doc-1", request.getData().get(0).get("documentId").getAsString());
        assertEquals("Keep section 3 unchanged", request.getData().get(0).get("summary").getAsString());
        assertEquals("task-1", request.getData().get(0).get("sourceTaskId").getAsString());
    }

    @Test
    void shouldApplyDocumentScopedFilterWhenSearchingTaskDecisions() {
        MilvusClientV2 milvusClient = mock(MilvusClientV2.class);
        when(milvusClient.search(any(SearchReq.class))).thenReturn(SearchResp.builder()
                .searchResults(List.of(List.of(SearchResp.SearchResult.builder()
                        .entity(Map.ofEntries(
                                Map.entry("memoryId", "memory-2"),
                                Map.entry("memoryType", "DOCUMENT_DECISION"),
                                Map.entry("documentId", "doc-1"),
                                Map.entry("summary", "Keep section 3 unchanged"),
                                Map.entry("details", "User accepted keeping section 3 structure unchanged"),
                                Map.entry("sourceTaskId", "task-1"),
                                Map.entry("sourceSessionId", "session-1"),
                                Map.entry("createdAt", "2026-04-03T10:00:00"),
                                Map.entry("updatedAt", "2026-04-03T10:05:00")
                        ))
                        .score(0.91f)
                        .build())))
                .build());
        MilvusLongTermMemoryRepository repository = new MilvusLongTermMemoryRepository(
                milvusClient,
                new LongTermMemoryMilvusProperties("long_term_memory_v1", 3)
        );

        List<LongTermMemoryItem> results = repository.searchConfirmedDocumentDecisions("doc-1", new float[]{0.1f, 0.2f, 0.3f}, 3);

        assertEquals(1, results.size());
        assertEquals("memory-2", results.get(0).getMemoryId());
        assertEquals("Keep section 3 unchanged", results.get(0).getSummary());
        assertEquals("doc-1", results.get(0).getDocumentId());
        assertNull(results.get(0).getEmbedding().length == 0 ? null : results.get(0).getEmbedding());

        ArgumentCaptor<SearchReq> requestCaptor = ArgumentCaptor.forClass(SearchReq.class);
        verify(milvusClient).search(requestCaptor.capture());
        assertEquals("long_term_memory_v1", requestCaptor.getValue().getCollectionName());
        assertEquals("memoryType == \"DOCUMENT_DECISION\" and documentId == \"doc-1\"", requestCaptor.getValue().getFilter());
    }

    @Test
    void shouldLoadMemoryByIdForReplaceFlow() {
        MilvusClientV2 milvusClient = mock(MilvusClientV2.class);
        when(milvusClient.query(any(QueryReq.class))).thenReturn(QueryResp.builder()
                .queryResults(List.of(QueryResp.QueryResult.builder()
                        .entity(Map.ofEntries(
                                Map.entry("memoryId", "memory-1"),
                                Map.entry("memoryType", "USER_PROFILE"),
                                Map.entry("summary", "Always answer in Chinese"),
                                Map.entry("details", "User explicitly prefers Chinese answers"),
                                Map.entry("sourceTaskId", "task-1"),
                                Map.entry("sourceSessionId", "session-1"),
                                Map.entry("createdAt", "2026-04-03T10:00:00"),
                                Map.entry("updatedAt", "2026-04-03T10:05:00")
                        ))
                        .build()))
                .build());
        MilvusLongTermMemoryRepository repository = new MilvusLongTermMemoryRepository(
                milvusClient,
                new LongTermMemoryMilvusProperties("long_term_memory_v1", 3)
        );

        Optional<LongTermMemoryItem> result = repository.findById("memory-1");

        assertTrue(result.isPresent());
        assertEquals("memory-1", result.get().getMemoryId());
        assertEquals("Always answer in Chinese", result.get().getSummary());

        ArgumentCaptor<QueryReq> requestCaptor = ArgumentCaptor.forClass(QueryReq.class);
        verify(milvusClient).query(requestCaptor.capture());
        assertEquals("memoryId == \"memory-1\"", requestCaptor.getValue().getFilter());
    }

    @Test
    void shouldDeleteMemoryById() {
        MilvusClientV2 milvusClient = mock(MilvusClientV2.class);
        MilvusLongTermMemoryRepository repository = new MilvusLongTermMemoryRepository(
                milvusClient,
                new LongTermMemoryMilvusProperties("long_term_memory_v1", 3)
        );

        repository.deleteMemory("memory-2");

        verify(milvusClient).delete(any());
    }

    private LongTermMemoryItem memory(String memoryId,
                                      LongTermMemoryType memoryType,
                                      String documentId,
                                      String summary,
                                      String details) {
        return new LongTermMemoryItem(
                memoryId,
                memoryType,
                documentId,
                summary,
                details,
                "task-1",
                "session-1",
                List.of("confirmed"),
                LocalDateTime.of(2026, 4, 3, 10, 0),
                LocalDateTime.of(2026, 4, 3, 10, 5),
                new float[]{0.1f, 0.2f, 0.3f}
        );
    }
}
