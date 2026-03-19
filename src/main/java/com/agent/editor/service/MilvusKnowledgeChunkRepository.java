package com.agent.editor.service;

import com.agent.editor.config.MilvusProperties;
import com.agent.editor.model.KnowledgeChunk;
import com.agent.editor.model.RetrievedKnowledgeChunk;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class MilvusKnowledgeChunkRepository implements KnowledgeChunkRepository {

    private static final String DOCUMENT_ID = "documentId";
    private static final String FILE_NAME = "fileName";
    private static final String CHUNK_INDEX = "chunkIndex";
    private static final String HEADING = "heading";
    private static final String CHUNK_TEXT = "chunkText";
    private static final String EMBEDDING = "embedding";

    private final MilvusClientV2 milvusClient;
    private final MilvusProperties properties;

    public MilvusKnowledgeChunkRepository(MilvusClientV2 milvusClient, MilvusProperties properties) {
        this.milvusClient = milvusClient;
        this.properties = properties;
    }

    @Override
    public List<KnowledgeChunk> findByDocumentIds(List<String> documentIds) {
        throw new UnsupportedOperationException("Milvus repository only supports vector retrieval");
    }

    @Override
    public void saveAll(List<KnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        milvusClient.upsert(UpsertReq.builder()
                .collectionName(properties.collectionName())
                .data(chunks.stream().map(this::toRow).toList())
                .build());
    }

    @Override
    public List<RetrievedKnowledgeChunk> searchByVector(float[] queryVector, List<String> documentIds, int topK) {
        SearchResp response = milvusClient.search(SearchReq.builder()
                .collectionName(properties.collectionName())
                .annsField(EMBEDDING)
                .metricType(IndexParam.MetricType.COSINE)
                .topK(topK)
                .filter(buildDocumentFilter(documentIds))
                .outputFields(List.of(DOCUMENT_ID, FILE_NAME, CHUNK_INDEX, HEADING, CHUNK_TEXT))
                .data(List.of(new FloatVec(queryVector)))
                .build());

        return response.getSearchResults().stream()
                .flatMap(List::stream)
                .map(this::toRetrievedChunk)
                .toList();
    }

    private JsonObject toRow(KnowledgeChunk chunk) {
        JsonObject row = new JsonObject();
        row.addProperty(DOCUMENT_ID, chunk.documentId());
        row.addProperty(FILE_NAME, chunk.fileName());
        row.addProperty(CHUNK_INDEX, chunk.chunkIndex());
        if (chunk.heading() != null) {
            row.addProperty(HEADING, chunk.heading());
        }
        row.addProperty(CHUNK_TEXT, chunk.chunkText());
        chunk.metadata().forEach(row::addProperty);
        row.add(EMBEDDING, toJsonArray(chunk.embedding()));
        return row;
    }

    private JsonArray toJsonArray(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("Knowledge chunk embedding is required for Milvus persistence");
        }

        JsonArray vector = new JsonArray();
        for (float value : embedding) {
            vector.add(value);
        }
        return vector;
    }

    private RetrievedKnowledgeChunk toRetrievedChunk(SearchResp.SearchResult hit) {
        Map<String, Object> entity = hit.getEntity();
        return new RetrievedKnowledgeChunk(
                asString(entity.get(DOCUMENT_ID)),
                asString(entity.get(FILE_NAME)),
                asInt(entity.get(CHUNK_INDEX)),
                asString(entity.get(HEADING)),
                asString(entity.get(CHUNK_TEXT)),
                hit.getScore() == null ? 0.0d : hit.getScore()
        );
    }

    private String buildDocumentFilter(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return null;
        }

        return DOCUMENT_ID + " in [" + documentIds.stream()
                .filter(Objects::nonNull)
                .map(this::quote)
                .reduce((left, right) -> left + ", " + right)
                .orElse("") + "]";
    }

    private String quote(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value).toLowerCase(Locale.ROOT));
    }
}
