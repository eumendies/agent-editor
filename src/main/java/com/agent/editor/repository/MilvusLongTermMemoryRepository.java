package com.agent.editor.repository;

import com.agent.editor.agent.core.memory.LongTermMemoryItem;
import com.agent.editor.agent.core.memory.LongTermMemoryType;
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
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Milvus-backed repository for confirmed long-term memories.
 */
public class MilvusLongTermMemoryRepository implements LongTermMemoryRepository {

    private static final String MEMORY_ID = "memoryId";
    private static final String MEMORY_TYPE = "memoryType";
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
    public LongTermMemoryItem createMemory(LongTermMemoryItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Long-term memory item is required");
        }
        milvusClient.upsert(UpsertReq.builder()
                .collectionName(properties.getCollectionName())
                .data(List.of(toRow(item)))
                .build());
        return item;
    }

    @Override
    public Optional<LongTermMemoryItem> findById(String memoryId) {
        if (memoryId == null || memoryId.isBlank()) {
            return Optional.empty();
        }
        QueryResp response = milvusClient.query(QueryReq.builder()
                .collectionName(properties.getCollectionName())
                .filter(buildMemoryIdFilter(memoryId))
                .outputFields(OUTPUT_FIELDS)
                .limit(1)
                .build());
        if (response == null || response.getQueryResults() == null) {
            return Optional.empty();
        }
        return response.getQueryResults().stream()
                .map(QueryResp.QueryResult::getEntity)
                .map(this::toMemoryItem)
                .findFirst();
    }

    @Override
    public void deleteMemory(String memoryId) {
        if (memoryId == null || memoryId.isBlank()) {
            return;
        }
        milvusClient.delete(DeleteReq.builder()
                .collectionName(properties.getCollectionName())
                .filter(buildMemoryIdFilter(memoryId))
                .build());
    }

    @Override
    public List<LongTermMemoryItem> listUserProfiles() {
        QueryResp response = milvusClient.query(QueryReq.builder()
                .collectionName(properties.getCollectionName())
                .filter(buildProfileFilter())
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
        item.setDocumentId(asString(entity.get(DOCUMENT_ID)));
        item.setSummary(asString(entity.get(SUMMARY)));
        item.setDetails(asString(entity.get(DETAILS)));
        item.setSourceTaskId(asString(entity.get(SOURCE_TASK_ID)));
        item.setSourceSessionId(asString(entity.get(SOURCE_SESSION_ID)));
        item.setCreatedAt(parseTime(entity.get(CREATED_AT)));
        item.setUpdatedAt(parseTime(entity.get(UPDATED_AT)));
        return item;
    }

    private String buildProfileFilter() {
        return MEMORY_TYPE + " == " + quote(LongTermMemoryType.USER_PROFILE.name());
    }

    private String buildMemoryIdFilter(String memoryId) {
        return MEMORY_ID + " == " + quote(memoryId);
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
