package com.agent.editor.service;

import com.agent.editor.model.KnowledgeChunk;
import com.agent.editor.model.KnowledgeDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryKnowledgeStore {

    private final Map<String, KnowledgeDocument> documents = new ConcurrentHashMap<>();
    private final Map<String, List<KnowledgeChunk>> chunksByDocumentId = new ConcurrentHashMap<>();

    public void saveDocument(KnowledgeDocument document) {
        documents.put(document.id(), document);
    }

    public KnowledgeDocument getDocument(String documentId) {
        return documents.get(documentId);
    }

    public List<KnowledgeDocument> getDocuments() {
        return new ArrayList<>(documents.values());
    }

    public void saveChunk(KnowledgeChunk chunk) {
        chunksByDocumentId.computeIfAbsent(chunk.documentId(), ignored -> new ArrayList<>()).add(chunk);
    }

    public List<KnowledgeChunk> getChunks(String documentId) {
        return chunksByDocumentId.getOrDefault(documentId, List.of());
    }
}
