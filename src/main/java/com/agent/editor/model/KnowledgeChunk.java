package com.agent.editor.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class KnowledgeChunk {

    private String documentId;
    private int chunkIndex;
    private String fileName;
    private String heading;
    private String chunkText;
    private Map<String, String> metadata = Map.of();
    private float[] embedding;

    public KnowledgeChunk(String documentId,
                          int chunkIndex,
                          String fileName,
                          String heading,
                          String chunkText,
                          Map<String, String> metadata) {
        this(documentId, chunkIndex, fileName, heading, chunkText, metadata, null);
    }

    public KnowledgeChunk(String documentId,
                          int chunkIndex,
                          String fileName,
                          String heading,
                          String chunkText,
                          Map<String, String> metadata,
                          float[] embedding) {
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.fileName = fileName;
        this.heading = heading;
        this.chunkText = chunkText;
        setMetadata(metadata);
        setEmbedding(embedding);
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public float[] getEmbedding() {
        return embedding == null ? null : embedding.clone();
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding == null ? null : embedding.clone();
    }

    public KnowledgeChunk withEmbedding(float[] embedding) {
        return new KnowledgeChunk(documentId, chunkIndex, fileName, heading, chunkText, metadata, embedding);
    }
}
