package com.agent.editor.repository;

import com.agent.editor.agent.v2.core.memory.LongTermMemoryItem;
import com.agent.editor.agent.v2.core.memory.LongTermMemoryType;
import com.agent.editor.config.LongTermMemoryMilvusProperties;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Milvus-backed repository for confirmed long-term memories.
 */
public class MilvusLongTermMemoryRepository implements LongTermMemoryRepository {

    private static final String MEMORY_ID = "memoryId";
    private static final String MEMORY_TYPE = "memoryType";
    private static final String SCOPE_KEY = "scopeKey";
    private static final String DOCUMENT_ID = "documentId";
    private static final String SUMMARY = "summary";
    private static final String DETAILS = "details";
    private static final String SOURCE_TASK_ID = "sourceTaskId";
    private static final String SOURCE_SESSION_ID = "sourceSessionId";
    private static final String CREATED_AT = "createdAt";
    private static final String UPDATED_AT = "updatedAt";
    private static final String EMBEDDING = "embedding";
    private static final List<String> OUTPUT_FIELDS = List.of(
            MEMORY_ID,
            MEMORY_TYPE,
            SCOPE_KEY,
            DOCUMENT_ID,
            SUMMARY,
            DETAILS,
            SOURCE_TASK_ID,
            SOURCE_SESSION_ID,
            CREATED_AT,
            UPDATED_AT
    );

    private final MilvusClientV2 milvusClient;
    private final LongTermMemoryMilvusProperties properties;

    public MilvusLongTermMemoryRepository(MilvusClientV2 milvusClient,
                                          LongTermMemoryMilvusProperties properties) {
        this.milvusClient = milvusClient;
        this.properties = properties;
    }

    @Override
    public void saveAll(List<LongTermMemoryItem> memories) {
        if (memories == null || memories.isEmpty()) {
            return;
        }

        milvusClient.upsert(UpsertReq.builder()
                .collectionName(properties.getCollectionName())
                .data(memories.stream().map(this::toRow).toList())
                .build());
    }

    @Override
    public List<LongTermMemoryItem> findConfirmedProfiles(String scopeKey) {
        QueryResp response = milvusClient.query(QueryReq.builder()
                .collectionName(properties.getCollectionName())
                .filter(buildProfileFilter(scopeKey))
                .outputFields(OUTPUT_FIELDS)
                .limit(20)
                .build());
        if (response == null || response.getQueryResults() == null) {
            return List.of();
        }
        return response.getQueryResults().stream()
                .map(QueryResp.QueryResult::getEntity)
                .map(this::toMemoryItem)
                .toList();
    }

    @Override
    public List<LongTermMemoryItem> searchConfirmedDocumentDecisions(String documentId,
                                                                     float[] queryVector,
                                                                     int topK) {
        SearchResp response = milvusClient.search(SearchReq.builder()
                .collectionName(properties.getCollectionName())
                .annsField(EMBEDDING)
                .metricType(IndexParam.MetricType.COSINE)
                .topK(topK)
                .filter(buildDocumentDecisionFilter(documentId))
                .outputFields(OUTPUT_FIELDS)
                .data(List.of(new FloatVec(queryVector)))
                .build());

        return response.getSearchResults().stream()
                .flatMap(List::stream)
                .map(SearchResp.SearchResult::getEntity)
                .map(this::toMemoryItem)
                .toList();
    }

    private JsonObject toRow(LongTermMemoryItem item) {
        JsonObject row = new JsonObject();
        row.addProperty(MEMORY_ID, item.getMemoryId());
        row.addProperty(MEMORY_TYPE, item.getMemoryType().name());
        row.addProperty(SCOPE_KEY, item.getScopeKey());
        row.add(DOCUMENT_ID, item.getDocumentId() == null ? JsonNull.INSTANCE : new JsonPrimitive(item.getDocumentId()));
        row.addProperty(SUMMARY, item.getSummary());
        row.addProperty(DETAILS, item.getDetails());
        row.addProperty(SOURCE_TASK_ID, item.getSourceTaskId());
        row.addProperty(SOURCE_SESSION_ID, item.getSourceSessionId());
        row.addProperty(CREATED_AT, item.getCreatedAt() == null ? null : item.getCreatedAt().toString());
        row.addProperty(UPDATED_AT, item.getUpdatedAt() == null ? null : item.getUpdatedAt().toString());
        row.add(EMBEDDING, toJsonArray(item.getEmbedding()));
        return row;
    }

    private JsonArray toJsonArray(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("Long-term memory embedding is required for Milvus persistence");
        }

        JsonArray vector = new JsonArray();
        for (float value : embedding) {
            vector.add(value);
        }
        return vector;
    }

    private LongTermMemoryItem toMemoryItem(Map<String, Object> entity) {
        LongTermMemoryItem item = new LongTermMemoryItem();
        item.setMemoryId(asString(entity.get(MEMORY_ID)));
        item.setMemoryType(LongTermMemoryType.valueOf(asString(entity.get(MEMORY_TYPE))));
        item.setScopeKey(asString(entity.get(SCOPE_KEY)));
        item.setDocumentId(asString(entity.get(DOCUMENT_ID)));
        item.setSummary(asString(entity.get(SUMMARY)));
        item.setDetails(asString(entity.get(DETAILS)));
        item.setSourceTaskId(asString(entity.get(SOURCE_TASK_ID)));
        item.setSourceSessionId(asString(entity.get(SOURCE_SESSION_ID)));
        item.setCreatedAt(parseTime(entity.get(CREATED_AT)));
        item.setUpdatedAt(parseTime(entity.get(UPDATED_AT)));
        return item;
    }

    private String buildProfileFilter(String scopeKey) {
        return MEMORY_TYPE + " == " + quote(LongTermMemoryType.USER_PROFILE.name())
                + " and " + SCOPE_KEY + " == " + quote(scopeKey);
    }

    private String buildDocumentDecisionFilter(String documentId) {
        String baseFilter = MEMORY_TYPE + " == " + quote(LongTermMemoryType.DOCUMENT_DECISION.name());
        if (documentId == null || documentId.isBlank()) {
            return baseFilter;
        }
        // document decision 先按文档裁剪，再做向量召回，避免不同文档间的旧决策串味。
        return baseFilter + " and " + DOCUMENT_ID + " == " + quote(documentId);
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private LocalDateTime parseTime(Object value) {
        String text = asString(value);
        return text == null || text.isBlank() ? null : LocalDateTime.parse(text);
    }

    private String quote(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }
}
