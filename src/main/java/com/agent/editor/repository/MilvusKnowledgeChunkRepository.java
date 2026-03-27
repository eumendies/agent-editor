package com.agent.editor.repository;

import com.agent.editor.config.MilvusProperties;
import com.agent.editor.model.KnowledgeChunk;
import com.agent.editor.model.RetrievedKnowledgeChunk;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class MilvusKnowledgeChunkRepository implements KnowledgeChunkRepository {

    private static final String ID = "id";
    private static final String DOCUMENT_ID = "documentId";
    private static final String FILE_NAME = "fileName";
    private static final String CHUNK_INDEX = "chunkIndex";
    private static final String HEADING = "heading";
    private static final String CHUNK_TEXT = "chunkText";
    private static final String FULL_TEXT = "fullText";
    private static final String SPARSE_FULL_TEXT = "sparseFullText";
    private static final String EMBEDDING = "embedding";
    private static final List<String> OUTPUT_FIELDS = List.of(DOCUMENT_ID, FILE_NAME, CHUNK_INDEX, HEADING, CHUNK_TEXT);

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
                .collectionName(properties.getCollectionName())
                .data(chunks.stream().map(this::toRow).toList())
                .build());
    }

    @Override
    public List<RetrievedKnowledgeChunk> searchHybrid(String query, float[] queryVector, List<String> documentIds, int topK) {
        int candidateTopK = Math.max(topK * 3, 20);
        try {
            SearchResp response = milvusClient.hybridSearch(HybridSearchReq.builder()
                    .collectionName(properties.getCollectionName())
                    .searchRequests(List.of(
                            AnnSearchReq.builder()
                                    .vectorFieldName(EMBEDDING)
                                    .metricType(IndexParam.MetricType.COSINE)
                                    .topK(candidateTopK)
                                    .filter(buildDocumentFilter(documentIds))
                                    .vectors(List.of(new FloatVec(queryVector)))
                                    .build(),
                            AnnSearchReq.builder()
                                    .vectorFieldName(SPARSE_FULL_TEXT)
                                    .metricType(IndexParam.MetricType.BM25)
                                    .topK(candidateTopK)
                                    .filter(buildDocumentFilter(documentIds))
                                    .vectors(List.of(new EmbeddedText(query)))
                                    .build()
                    ))
                    .topK(topK)
                    .outFields(OUTPUT_FIELDS)
                    .build());
            return toRetrievedChunks(response);
        } catch (RuntimeException error) {
            return searchByVector(queryVector, documentIds, topK);
        }
    }

    @Override
    public List<RetrievedKnowledgeChunk> searchByVector(float[] queryVector, List<String> documentIds, int topK) {
        SearchResp response = milvusClient.search(SearchReq.builder()
                .collectionName(properties.getCollectionName())
                .annsField(EMBEDDING)
                .metricType(IndexParam.MetricType.COSINE)
                .topK(topK)
                .filter(buildDocumentFilter(documentIds))
                .outputFields(OUTPUT_FIELDS)
                .data(List.of(new FloatVec(queryVector)))
                .build());

        return toRetrievedChunks(response);
    }

    private List<RetrievedKnowledgeChunk> toRetrievedChunks(SearchResp response) {
        return response.getSearchResults().stream()
                .flatMap(List::stream)
                .map(this::toRetrievedChunk)
                .toList();
    }

    private JsonObject toRow(KnowledgeChunk chunk) {
        JsonObject row = new JsonObject();
        row.addProperty(ID, chunkId(chunk));
        row.addProperty(DOCUMENT_ID, chunk.getDocumentId());
        row.addProperty(FILE_NAME, chunk.getFileName());
        row.addProperty(CHUNK_INDEX, chunk.getChunkIndex());
        if (chunk.getHeading() != null) {
            row.addProperty(HEADING, chunk.getHeading());
        }
        row.addProperty(CHUNK_TEXT, chunk.getChunkText());
        row.addProperty(FULL_TEXT, buildFullText(chunk));
        chunk.getMetadata().forEach(row::addProperty);
        row.add(EMBEDDING, toJsonArray(chunk.getEmbedding()));
        return row;
    }

    private String buildFullText(KnowledgeChunk chunk) {
        if (chunk.getHeading() == null || chunk.getHeading().isBlank()) {
            return chunk.getChunkText();
        }
        return chunk.getHeading() + "\n" + chunk.getChunkText();
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

    private String chunkId(KnowledgeChunk chunk) {
        return chunk.getDocumentId() + "#" + chunk.getChunkIndex();
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
