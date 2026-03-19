package com.agent.editor.service;

import com.agent.editor.config.RagProperties;
import com.agent.editor.model.KnowledgeChunk;
import com.agent.editor.model.RetrievedKnowledgeChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeRetrievalServiceTest {

    @Test
    void shouldReturnChunksOrderedByLexicalScore() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        store.saveChunk(new KnowledgeChunk("doc-1", 0, "resume.md", "项目经历", "Spring Boot 项目经验", Map.of()));
        store.saveChunk(new KnowledgeChunk("doc-1", 1, "resume.md", "其他", "Redis 缓存经验", Map.of()));

        KnowledgeRetrievalService service = new KnowledgeRetrievalService(store, new RagProperties(500, 80, 5, 8, 12));
        List<RetrievedKnowledgeChunk> chunks = service.retrieve("Spring Boot", null, null);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).chunkText().contains("Spring Boot"));
        assertTrue(chunks.get(0).score() > 0);
    }
}
