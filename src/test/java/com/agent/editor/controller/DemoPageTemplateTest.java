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
        assertTrue(template.contains("messageList"));
        assertTrue(template.contains("chatComposer"));
        assertTrue(template.contains("sendButton"));
        assertTrue(template.contains("sendCurrentMessage"));
        assertTrue(template.contains("width: min(100%, 520px);"));
        assertTrue(template.contains("width: min(100%, 420px);"));
        assertTrue(template.contains("border-style: dashed;"));
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
        assertTrue(template.contains("pendingDiffActions"));
        assertTrue(template.contains("pendingDiffStatus"));
        assertTrue(template.contains(".diff-view"));
        assertTrue(template.contains("max-height:"));
        assertTrue(template.contains("overflow: auto;"));
        assertTrue(template.contains("loadPendingDiff"));
        assertTrue(template.contains("applyPendingDiff"));
        assertTrue(template.contains("discardPendingDiff"));
        assertTrue(template.contains("确认应用"));
        assertTrue(template.contains("放弃修改"));
        assertFalse(template.contains("await refreshDocument(currentTaskDocumentId);"));
        assertFalse(template.contains("Scenario Bar"));
        assertFalse(template.contains("Mode Lens"));
        assertFalse(template.contains("Agent V2 Orchestration Demo"));
        assertFalse(template.contains("Final Result"));
        assertFalse(template.contains("chat-session"));
        assertFalse(template.contains("tracePanel"));
        assertFalse(template.contains("clearChatBtn"));
        assertFalse(template.contains("reactBtn"));
    }

    @Test
    void shouldLoadDocumentOptionsFromApi() throws IOException {
        String template = Files.readString(Path.of("src/main/resources/templates/index.html"));

        assertTrue(template.contains("async function loadDocumentOptions"));
        assertTrue(template.contains("fetch(\"/api/v1/documents\")"));
        assertTrue(template.contains("documentSelect.innerHTML = documents.map"));
        assertTrue(template.contains("await loadDocumentOptions();"));
        assertFalse(template.contains("<option value=\"doc-001\">Sample Document</option>"));
    }
}
