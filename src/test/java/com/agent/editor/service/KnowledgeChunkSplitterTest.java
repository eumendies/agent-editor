package com.agent.editor.service;

import com.agent.editor.config.RagProperties;
import com.agent.editor.model.KnowledgeChunk;
import com.agent.editor.utils.rag.KnowledgeChunkSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeChunkSplitterTest {

    @Test
    void shouldSplitByHeadingAndKeepMetadata() {
        RagProperties properties = new RagProperties(500, 80, 5, 8, 12);
        KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(properties);
        String content = "# 项目经历\nJava Spring Boot ElasticSearch RabbitMQ\n\n# 实习经历\nWebFlux Flowable";

        List<KnowledgeChunk> chunks = splitter.split("doc-1", "resume.md", content, Map.of("category", "resume"));

        assertEquals(2, chunks.size());
        assertEquals(0, chunks.get(0).getChunkIndex());
        assertEquals("项目经历", chunks.get(0).getHeading());
        assertEquals("实习经历", chunks.get(1).getHeading());
    }

    @Test
    void shouldRecursivelySplitMarkdownByNestedHeadings() {
        RagProperties properties = new RagProperties(80, 10, 5, 8, 12);
        KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(properties);
        String content = """
                # 项目经历
                ## Agent Editor
                Agent Editor 项目内容 Agent Editor 项目内容 Agent Editor 项目内容

                ## 智能检索
                智能检索项目内容 智能检索项目内容 智能检索项目内容
                """;

        List<KnowledgeChunk> chunks = splitter.split("doc-1", "resume.md", content, Map.of("category", "resume"));

        assertEquals(2, chunks.size());
        assertEquals("项目经历 > Agent Editor", chunks.get(0).getHeading());
        assertEquals("项目经历 > 智能检索", chunks.get(1).getHeading());
    }

    @Test
    void shouldFallbackToSlidingWindowWhenLeafSectionExceedsChunkSize() {
        RagProperties properties = new RagProperties(60, 10, 5, 8, 12);
        KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(properties);
        String content = """
                # 项目经历
                ## Agent Editor
                这里是一段特别长的叶子章节内容，这里是一段特别长的叶子章节内容，这里是一段特别长的叶子章节内容，
                这里是一段特别长的叶子章节内容，这里是一段特别长的叶子章节内容。
                """;

        List<KnowledgeChunk> chunks = splitter.split("doc-1", "resume.md", content, Map.of("category", "resume"));

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> "项目经历 > Agent Editor".equals(chunk.getHeading())));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getChunkText().length() <= 60));
    }

    @Test
    void shouldBuildFullHeadingPathForNestedMarkdownSections() {
        RagProperties properties = new RagProperties(30, 10, 5, 8, 12);
        KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(properties);
        String content = """
                # 项目经历
                ## Agent Editor
                ### 检索增强
                负责 Markdown 递归分块
                """;

        List<KnowledgeChunk> chunks = splitter.split("doc-1", "resume.md", content, Map.of());

        assertEquals(1, chunks.size());
        assertEquals("项目经历 > Agent Editor > 检索增强", chunks.get(0).getHeading());
        assertTrue(chunks.get(0).getChunkText().startsWith("### 检索增强"));
    }

    @Test
    void shouldKeepHeadingNullForHeadinglessMarkdown() {
        RagProperties properties = new RagProperties(20, 5, 5, 8, 12);
        KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(properties);

        List<KnowledgeChunk> chunks = splitter.split("doc-1", "notes.md", "纯正文内容纯正文内容纯正文内容纯正文内容", Map.of());

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getHeading() == null));
    }

    @Test
    void shouldPreferChildSectionsOverDuplicatingOversizedParentSection() {
        RagProperties properties = new RagProperties(40, 10, 5, 8, 12);
        KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(properties);
        String content = """
                # 项目经历
                父章节前言父章节前言父章节前言

                ## Agent Editor
                子章节内容子章节内容子章节内容
                """;

        List<KnowledgeChunk> chunks = splitter.split("doc-1", "resume.md", content, Map.of());

        assertEquals(2, chunks.size());
        assertEquals("项目经历", chunks.get(0).getHeading());
        assertEquals("项目经历 > Agent Editor", chunks.get(1).getHeading());
    }

    @Test
    void shouldKeepHeadingNullForNonMarkdownTextChunks() {
        RagProperties properties = new RagProperties(20, 5, 5, 8, 12);
        KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(properties);

        List<KnowledgeChunk> chunks = splitter.split("doc-1", "notes.txt", "纯正文内容纯正文内容纯正文内容纯正文内容", Map.of());

        assertFalse(chunks.isEmpty());
        assertNull(chunks.get(0).getHeading());
    }
}
