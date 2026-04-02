package com.agent.editor.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoPageTemplateTest {

    @Test
    void shouldExposeOrchestrationDemoCopy() throws IOException {
        String template = Files.readString(Path.of("src/main/resources/templates/index.html"));

        assertTrue(template.contains("Document Workspace"));
        assertTrue(template.contains("Chat Workspace"));
        assertTrue(template.contains("documentEditor"));
        assertTrue(template.contains("saveDocumentBtn"));
        assertTrue(template.contains("saveStatus"));
        assertTrue(template.contains("chatMessages"));
        assertTrue(template.contains("clearChatBtn"));
        assertTrue(template.contains("Knowledge Base Upload"));
        assertTrue(template.contains("knowledgeUploadForm"));
        assertTrue(template.contains("knowledgeFileInput"));
        assertTrue(template.contains("knowledgeCategoryInput"));
        assertTrue(template.contains("knowledgeUploadResult"));
        assertTrue(template.contains("saveDocument"));
        assertTrue(template.contains("lastCompletedStreamText"));
        assertTrue(template.contains("shouldSuppressCompletedMessage"));
        assertTrue(template.contains("TASK_COMPLETED"));
        assertTrue(template.contains("SUPERVISOR_COMPLETED"));
        assertTrue(template.contains("response.status !== 202"));
        assertTrue(template.contains("startTaskStatusPolling"));
        assertTrue(template.contains("finalizeTask"));
        assertTrue(template.contains("currentTaskDocumentId"));
        assertTrue(template.contains("refreshDocument(currentTaskDocumentId)"));
        assertFalse(template.contains("Scenario Bar"));
        assertFalse(template.contains("Mode Lens"));
        assertFalse(template.contains("Agent V2 Orchestration Demo"));
    }
}
