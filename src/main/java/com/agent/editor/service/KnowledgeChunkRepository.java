package com.agent.editor.service;

import com.agent.editor.model.KnowledgeChunk;
import com.agent.editor.model.RetrievedKnowledgeChunk;

import java.util.List;

public interface KnowledgeChunkRepository {

    List<KnowledgeChunk> findByDocumentIds(List<String> documentIds);

    default void saveAll(List<KnowledgeChunk> chunks) {
        throw new UnsupportedOperationException("Chunk persistence is not implemented");
    }

    default List<RetrievedKnowledgeChunk> searchByVector(float[] queryVector, List<String> documentIds, int topK) {
        throw new UnsupportedOperationException("Vector search is not implemented");
    }
}
