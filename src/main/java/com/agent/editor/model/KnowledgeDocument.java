package com.agent.editor.model;

import java.time.Instant;

public record KnowledgeDocument(
        String id,
        String fileName,
        String category,
        String status,
        Instant createdAt
) {
}
