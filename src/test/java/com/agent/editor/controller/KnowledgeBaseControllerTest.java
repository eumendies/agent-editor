package com.agent.editor.controller;

import com.agent.editor.model.KnowledgeDocument;
import com.agent.editor.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseControllerTest {

    @Test
    void shouldDelegateUploadToKnowledgeBaseService() {
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        KnowledgeBaseController controller = new KnowledgeBaseController(knowledgeBaseService);
        MockMultipartFile file = new MockMultipartFile("file", "resume.md", "text/markdown", "# Resume".getBytes());
        KnowledgeDocument document = new KnowledgeDocument("doc-1", "resume.md", "resume", "PENDING", Instant.now());

        when(knowledgeBaseService.upload(file, "resume")).thenReturn(document);

        KnowledgeDocument result = controller.upload(file, "resume");

        assertSame(document, result);
        verify(knowledgeBaseService).upload(file, "resume");
    }
}
