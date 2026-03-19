package com.agent.editor.model;

import java.util.Map;

public record KnowledgeChunk(
        String documentId,
        int chunkIndex,
        String fileName,
        String heading,
        String chunkText,
        Map<String, String> metadata
) {
}
