package com.agent.editor.service;

import com.agent.editor.model.KnowledgeChunk;

import java.util.List;

public interface KnowledgeChunkRepository {

    List<KnowledgeChunk> findByDocumentIds(List<String> documentIds);
}
