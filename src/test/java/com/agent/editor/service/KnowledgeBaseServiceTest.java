package com.agent.editor.service;

import com.agent.editor.model.KnowledgeDocument;
import com.agent.editor.repository.InMemoryKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeBaseServiceTest {

    @Test
    void shouldCreatePendingKnowledgeDocumentRecord() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        KnowledgeBaseService service = new KnowledgeBaseService(store, store, null, null, null, null);
        MockMultipartFile file = new MockMultipartFile("file", "resume.md", "text/markdown", "# Resume".getBytes());

        KnowledgeDocument document = service.upload(file, "resume");

        assertEquals("resume.md", document.fileName());
        assertEquals("resume", document.category());
        assertEquals("PENDING", document.status());
    }
}
