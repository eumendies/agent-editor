package com.agent.editor.repository;

import com.agent.editor.model.KnowledgeChunk;
import com.agent.editor.model.KnowledgeDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryKnowledgeStore implements KnowledgeChunkRepository {

    private final Map<String, KnowledgeDocument> documents = new ConcurrentHashMap<>();
    private final Map<String, List<KnowledgeChunk>> chunksByDocumentId = new ConcurrentHashMap<>();

    public void saveDocument(KnowledgeDocument document) {
        documents.put(document.getId(), document);
    }

    public KnowledgeDocument getDocument(String documentId) {
        return documents.get(documentId);
    }

    public List<KnowledgeDocument> getDocuments() {
        return new ArrayList<>(documents.values());
    }

    public void saveChunk(KnowledgeChunk chunk) {
        chunksByDocumentId.computeIfAbsent(chunk.getDocumentId(), ignored -> new ArrayList<>()).add(chunk);
    }

    @Override
    public void saveAll(List<KnowledgeChunk> chunks) {
        chunks.forEach(this::saveChunk);
    }

    public List<KnowledgeChunk> getChunks(String documentId) {
        return chunksByDocumentId.getOrDefault(documentId, List.of());
    }

    @Override
    public List<KnowledgeChunk> findByDocumentIds(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return chunksByDocumentId.values().stream()
                    .flatMap(List::stream)
                    .toList();
        }

        return documentIds.stream()
                .flatMap(documentId -> getChunks(documentId).stream())
                .toList();
    }
}
