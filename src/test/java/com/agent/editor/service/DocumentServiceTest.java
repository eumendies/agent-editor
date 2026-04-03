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
        assertEquals(1, service.getAllDocuments().size());

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

        assertTrue(content.startsWith("本文是一份用于验证结构化文档编辑能力的示例文档。"));
        assertTrue(content.contains("# 项目概览"));
        assertTrue(content.contains("## 编辑目标"));
        assertTrue(content.contains("> 说明：默认文档应当足够结构化"));
        assertTrue(content.contains("```json"));
    }
}
