package com.agent.editor.service;

import com.agent.editor.config.RagProperties;
import com.agent.editor.model.KnowledgeChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeChunkSplitterTest {

    @Test
    void shouldSplitByHeadingAndKeepMetadata() {
        RagProperties properties = new RagProperties(500, 80, 5, 8, 12);
        KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(properties);
        String content = "# 项目经历\nJava Spring Boot ElasticSearch RabbitMQ\n\n# 实习经历\nWebFlux Flowable";

        List<KnowledgeChunk> chunks = splitter.split("doc-1", "resume.md", content, Map.of("category", "resume"));

        assertTrue(chunks.size() >= 2);
        assertEquals(0, chunks.get(0).chunkIndex());
        assertNotNull(chunks.get(0).heading());
    }
}
