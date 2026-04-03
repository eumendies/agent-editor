package com.agent.editor.service;

import com.agent.editor.agent.v2.core.memory.LongTermMemoryItem;
import com.agent.editor.model.RetrievedLongTermMemory;
import com.agent.editor.repository.LongTermMemoryRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LongTermMemoryRetrievalService {

    private static final String DEFAULT_PROFILE_SCOPE = "default";

    private final LongTermMemoryRepository repository;
    private final KnowledgeEmbeddingService embeddingService;

    @Autowired
    public LongTermMemoryRetrievalService(ObjectProvider<LongTermMemoryRepository> repositoryProvider,
                                          KnowledgeEmbeddingService embeddingService) {
        this(repositoryProvider.getIfAvailable(), embeddingService);
    }

    LongTermMemoryRetrievalService(LongTermMemoryRepository repository,
                                   KnowledgeEmbeddingService embeddingService) {
        this.repository = repository;
        this.embeddingService = embeddingService;
    }

    public List<LongTermMemoryItem> loadConfirmedProfiles() {
        if (repository == null) {
            return List.of();
        }
        return repository.findConfirmedProfiles(DEFAULT_PROFILE_SCOPE);
    }

    public List<RetrievedLongTermMemory> searchConfirmedTaskDecisions(String query,
                                                                      String documentId,
                                                                      Integer topK) {
        if (repository == null || query == null || query.isBlank()) {
            return List.of();
        }
        int limit = topK == null || topK <= 0 ? 3 : topK;
        float[] queryVector = embeddingService.embed(query);
        return repository.searchConfirmedTaskDecisions(documentId, queryVector, limit).stream()
                .map(memory -> new RetrievedLongTermMemory(
                        memory.getMemoryId(),
                        memory.getMemoryType().name(),
                        memory.getSummary(),
                        buildRelevanceReason(memory, documentId),
                        memory.getSourceTaskId(),
                        memory.getCreatedAt() == null ? null : memory.getCreatedAt().toString()
                ))
                .toList();
    }

    private String buildRelevanceReason(LongTermMemoryItem memory, String documentId) {
        // 先返回稳定、可解释的检索理由，避免把内部 details 原样泄露给模型。
        if (documentId != null && !documentId.isBlank()) {
            return "Matched the current editing direction for " + documentId;
        }
        return "Matched the current editing direction";
    }
}
