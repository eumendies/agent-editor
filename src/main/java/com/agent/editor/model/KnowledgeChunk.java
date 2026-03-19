package com.agent.editor.model;

import java.util.Map;

public record KnowledgeChunk(
        String documentId,
        int chunkIndex,
        String fileName,
        String heading,
        String chunkText,
        Map<String, String> metadata,
        float[] embedding
) {

    public KnowledgeChunk(String documentId,
                          int chunkIndex,
                          String fileName,
                          String heading,
                          String chunkText,
                          Map<String, String> metadata) {
        this(documentId, chunkIndex, fileName, heading, chunkText, metadata, null);
    }

    public KnowledgeChunk {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        embedding = embedding == null ? null : embedding.clone();
    }

    @Override
    public float[] embedding() {
        return embedding == null ? null : embedding.clone();
    }

    public KnowledgeChunk withEmbedding(float[] embedding) {
        return new KnowledgeChunk(documentId, chunkIndex, fileName, heading, chunkText, metadata, embedding);
    }
}
