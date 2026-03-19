package com.agent.editor.model;

public record RetrievedKnowledgeChunk(
        String documentId,
        String fileName,
        int chunkIndex,
        String heading,
        String chunkText,
        double score
) {
}
