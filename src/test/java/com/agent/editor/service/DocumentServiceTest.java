package com.agent.editor.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentServiceTest {

    @Test
    void shouldKeepDocumentServiceFocusedOnDocumentsAndDiffs() {
        DocumentService service = new DocumentService();

        assertNotNull(service.getDocument("doc-001"));
        assertNotNull(service.getDocument("doc-002"));
        assertEquals(2, service.getAllDocuments().size());

        Set<String> publicMethods = Arrays.stream(DocumentService.class.getMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertFalse(publicMethods.contains("executeAgentTask"));
        assertFalse(publicMethods.contains("startAgentTaskAsync"));
        assertFalse(publicMethods.contains("getTaskStatus"));
        assertFalse(publicMethods.contains("getTaskSteps"));
        assertFalse(publicMethods.contains("buildSnapshot"));
        assertFalse(publicMethods.contains("readNode"));
        assertFalse(publicMethods.contains("applyPatch"));
    }

    @Test
    void shouldSeedDefaultDocumentWithStructuredMarkdownContent() {
        DocumentService service = new DocumentService();

        String content = service.getDocument("doc-001").getContent();
        String shortDocumentContent = service.getDocument("doc-002").getContent();

        assertTrue(content.startsWith("# LangChain 入门指南"));
        assertTrue(content.contains("## 目录"));
        assertTrue(content.contains("### 什么是 LangChain？"));
        assertTrue(content.contains("## 快速开始"));
        assertTrue(content.contains("```python"));
        assertTrue(shortDocumentContent.startsWith("多年以后，面对行刑队"));
    }
}
