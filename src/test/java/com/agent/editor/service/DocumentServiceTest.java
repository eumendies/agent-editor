package com.agent.editor.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    }
}
