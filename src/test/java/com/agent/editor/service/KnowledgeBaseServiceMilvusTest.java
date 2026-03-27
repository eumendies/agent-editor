package com.agent.editor.service;

import com.agent.editor.model.KnowledgeChunk;
import com.agent.editor.model.ParsedKnowledgeDocument;
import com.agent.editor.repository.InMemoryKnowledgeStore;
import com.agent.editor.repository.KnowledgeChunkRepository;
import com.agent.editor.utils.rag.KnowledgeChunkSplitter;
import com.agent.editor.utils.rag.KnowledgeDocumentParser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseServiceMilvusTest {

    @Test
    void shouldEmbedAndPersistChunksDuringUpload() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        KnowledgeChunkRepository repository = mock(KnowledgeChunkRepository.class);
        KnowledgeDocumentParser parser = mock(KnowledgeDocumentParser.class);
        KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(new com.agent.editor.config.RagProperties(500, 80, 5, 8, 12));
        KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
        KnowledgeBaseService service = new KnowledgeBaseService(
                store,
                repository,
                parser,
                splitter,
                embeddingService,
                null
        );
        MockMultipartFile file = new MockMultipartFile("file", "resume.md", "text/markdown", "# 项目经历\nSpring Boot 项目经验".getBytes());
        when(parser.parse(file)).thenReturn(new ParsedKnowledgeDocument("# 项目经历\nSpring Boot 项目经验", "markdown"));
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        service.upload(file, "resume");

        ArgumentCaptor<List<KnowledgeChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(chunksCaptor.capture());
        assertEquals(1, chunksCaptor.getValue().size());
        assertEquals("resume.md", chunksCaptor.getValue().get(0).getFileName());
        assertEquals("resume", chunksCaptor.getValue().get(0).getMetadata().get("category"));
        assertEquals("markdown", chunksCaptor.getValue().get(0).getMetadata().get("documentType"));
        assertArrayEquals(new float[]{0.1f, 0.2f, 0.3f}, chunksCaptor.getValue().get(0).getEmbedding());
    }

    @Test
    void shouldPersistMarkdownChunksWithHeadingPath() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        KnowledgeChunkRepository repository = mock(KnowledgeChunkRepository.class);
        KnowledgeDocumentParser parser = mock(KnowledgeDocumentParser.class);
        KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(new com.agent.editor.config.RagProperties(30, 10, 5, 8, 12));
        KnowledgeBaseService service = new KnowledgeBaseService(
                store,
                repository,
                parser,
                splitter,
                null,
                null
        );
        String content = """
                # 项目经历
                ## Agent Editor
                负责 Markdown 递归分块
                """;
        MockMultipartFile file = new MockMultipartFile("file", "resume.md", "text/markdown", content.getBytes(StandardCharsets.UTF_8));
        when(parser.parse(file)).thenReturn(new ParsedKnowledgeDocument(content, "markdown"));

        service.upload(file, "resume");

        ArgumentCaptor<List<KnowledgeChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(chunksCaptor.capture());
        assertEquals("项目经历 > Agent Editor", chunksCaptor.getValue().get(0).getHeading());
        assertTrue(chunksCaptor.getValue().stream()
                .allMatch(chunk -> "项目经历 > Agent Editor".equals(chunk.getHeading())));
    }
}
