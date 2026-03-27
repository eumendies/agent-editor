package com.agent.editor.service;

import com.agent.editor.config.RagProperties;
import com.agent.editor.model.RetrievedKnowledgeChunk;
import com.agent.editor.repository.KnowledgeChunkRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeRetrievalService {

    private final KnowledgeChunkRepository repository;
    private final KnowledgeEmbeddingService embeddingService;
    private final RagProperties properties;

    public KnowledgeRetrievalService(KnowledgeChunkRepository repository,
                                     KnowledgeEmbeddingService embeddingService,
                                     RagProperties properties) {
        this.repository = repository;
        this.embeddingService = embeddingService;
        this.properties = properties;
    }

    public List<RetrievedKnowledgeChunk> retrieve(String query, List<String> documentIds, Integer topK) {
        int limit = topK == null || topK <= 0 ? properties.getAskTopK() : topK;
        if (query == null || query.isBlank()) {
            return List.of();
        }
        float[] queryVector = embeddingService.embed(query);
        return repository.searchHybrid(query, queryVector, documentIds, limit);
    }
}
