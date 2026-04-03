package com.agent.editor.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
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

    @Test
    void shouldLoadSeededDocumentsFromConfiguredResourceLoader() {
        ResourceLoader resourceLoader = new StubResourceLoader(Map.of(
                "classpath:documents/doc-001.md", "# Stub Long Document\n\nfrom loader\n",
                "classpath:documents/doc-002.md", "stub short document"
        ));

        DocumentService service = new DocumentService(resourceLoader);

        assertEquals("# Stub Long Document\n\nfrom loader\n", service.getDocument("doc-001").getContent());
        assertEquals("stub short document", service.getDocument("doc-002").getContent());
    }

    private static final class StubResourceLoader implements ResourceLoader {

        private final Map<String, String> resources;

        private StubResourceLoader(Map<String, String> resources) {
            this.resources = resources;
        }

        @Override
        public Resource getResource(String location) {
            String content = resources.get(location);
            if (content == null) {
                throw new IllegalArgumentException("Missing stub resource: " + location);
            }
            return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public ClassLoader getClassLoader() {
            return getClass().getClassLoader();
        }
    }
}
