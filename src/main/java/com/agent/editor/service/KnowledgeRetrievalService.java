package com.agent.editor.service;

import com.agent.editor.config.RagProperties;
import com.agent.editor.model.KnowledgeChunk;
import com.agent.editor.model.RetrievedKnowledgeChunk;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class KnowledgeRetrievalService {

    private final KnowledgeChunkRepository repository;
    private final RagProperties properties;

    public KnowledgeRetrievalService(KnowledgeChunkRepository repository, RagProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public List<RetrievedKnowledgeChunk> retrieve(String query, List<String> documentIds, Integer topK) {
        int limit = topK == null || topK <= 0 ? properties.askTopK() : topK;
        return repository.findByDocumentIds(documentIds).stream()
                .map(chunk -> toRetrievedChunk(query, chunk))
                .filter(chunk -> chunk.score() > 0)
                .sorted(Comparator.comparingDouble(RetrievedKnowledgeChunk::score).reversed())
                .limit(limit)
                .toList();
    }

    private RetrievedKnowledgeChunk toRetrievedChunk(String query, KnowledgeChunk chunk) {
        return new RetrievedKnowledgeChunk(
                chunk.documentId(),
                chunk.fileName(),
                chunk.chunkIndex(),
                chunk.heading(),
                chunk.chunkText(),
                lexicalScore(query, chunk.chunkText())
        );
    }

    private double lexicalScore(String query, String chunkText) {
        if (query == null || query.isBlank() || chunkText == null || chunkText.isBlank()) {
            return 0;
        }

        String normalizedChunk = chunkText.toLowerCase(Locale.ROOT);
        return Arrays.stream(query.toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(token -> !token.isBlank())
                .mapToDouble(token -> normalizedChunk.contains(token) ? 1.0 : 0.0)
                .sum();
    }
}
