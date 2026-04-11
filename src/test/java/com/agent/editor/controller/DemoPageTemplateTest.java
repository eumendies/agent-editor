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

        assertTrue(template.contains("Agent Editor"));
        assertTrue(template.contains("figma-topbar"));
        assertTrue(template.contains("app-workspace"));
        assertTrue(template.contains("document-column"));
        assertTrue(template.contains("workspace-resizer"));
        assertTrue(template.contains("initWorkspaceResizer"));
        assertTrue(template.contains("assistant-panel"));
        assertTrue(template.contains("--assistant-width"));
        assertTrue(template.contains("display: flex;"));
        assertTrue(template.contains("documentEditor"));
        assertTrue(template.contains("saveDocumentBtn"));
        assertTrue(template.contains("saveStatus"));
        assertTrue(template.contains("messageList"));
        assertTrue(template.contains("输入任务后，事件流和回答会按聊天消息的方式出现在这里。"));
        assertTrue(template.contains("chatComposer"));
        assertTrue(template.contains("sendButton"));
        assertTrue(template.contains("sendCurrentMessage"));
        assertTrue(template.contains("autoResizeInstructionInput"));
        assertTrue(template.contains("max-height: 140px;"));
        assertTrue(template.contains("<option value=\"REACT\">ReAct</option>"));
        assertTrue(template.contains("<option value=\"PLANNING\">Planning</option>"));
        assertTrue(template.contains("<option value=\"REFLEXION\">Reflexion</option>"));
        assertTrue(template.contains("<option value=\"SUPERVISOR\">Supervisor</option>"));
        assertTrue(template.contains(".assistant-message.system"));
        assertTrue(template.contains(".assistant-message.user"));
        assertTrue(template.contains("composer-shell"));
        assertTrue(template.contains("knowledgeUploadForm"));
        assertTrue(template.contains("knowledgeFileInput"));
        assertTrue(template.contains("knowledgeCategoryInput"));
        assertTrue(template.contains("knowledgeUploadResult"));
        assertTrue(template.contains("userProfilePanel"));
        assertTrue(template.contains("newUserProfileInput"));
        assertTrue(template.contains("userProfileList"));
        assertTrue(template.contains("saveDocument"));
        assertTrue(template.contains("lastCompletedStreamText"));
        assertTrue(template.contains("shouldSuppressCompletedMessage"));
        assertTrue(template.contains("TASK_COMPLETED"));
        assertTrue(template.contains("SUPERVISOR_COMPLETED"));
        assertTrue(template.contains("response.status !== 202"));
        assertTrue(template.contains("startTaskStatusPolling"));
        assertTrue(template.contains("finalizeTask"));
        assertTrue(template.contains("const hasPendingDiff = await loadPendingDiff(currentTaskDocumentId);"));
        assertTrue(template.contains("if (hasPendingDiff === true)"));
        assertTrue(template.contains("PENDING_DIFF_READY_MESSAGE"));
        assertTrue(template.contains("NO_DOCUMENT_CHANGE_MESSAGE"));
        assertTrue(template.contains("Agent 未生成文档改动。"));
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
        assertFalse(template.contains("AI Document Editor UI Design"));
        assertFalse(template.contains("brand-dot"));
        assertFalse(template.contains("ai-badge"));
        assertFalse(template.contains("share-button"));
        assertFalse(template.contains("<option value=\"REACT\">Editor</option>"));
        assertFalse(template.contains("I've reviewed your document"));
        assertFalse(template.contains("Thanks. Can you make it sound a bit more professional?"));
        assertFalse(template.contains("Sure, I have updated the tone"));
        assertFalse(template.contains("Document Workspace"));
        assertFalse(template.contains("Chat Workspace"));
        assertFalse(template.contains("radial-gradient(circle"));
        assertFalse(template.contains("border-radius: 24px;"));
        assertFalse(template.contains("Scenario Bar"));
        assertFalse(template.contains("Mode Lens"));
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

    @Test
    void shouldKeepDiffPanelScrollableAndClearDiffAfterApplyingPendingChange() throws IOException {
        String template = Files.readString(Path.of("src/main/resources/templates/index.html"));

        assertTrue(template.contains(".diff-section {"));
        assertTrue(template.contains("min-height: 0;"));
        assertTrue(template.contains("overflow: hidden;"));
        assertTrue(template.contains("renderDiffEmptyState(\"当前没有待确认改动。\", \"已将候选修改应用到文档。\")"));
        assertFalse(template.contains("""
            await refreshDocument(documentId);
            await loadPendingDiff(documentId);
            renderSaveStatus("success", "已将候选修改应用到文档。");
            """));
    }

    @Test
    void shouldKeepUserProfilePanelScrollableWhenMemoriesGrow() throws IOException {
        String template = Files.readString(Path.of("src/main/resources/templates/index.html"));
        String profileSectionBlock = cssBlock(template, ".profile-section");
        String profileListBlock = cssBlock(template, ".user-profile-list");

        assertTrue(profileSectionBlock.contains("flex: 0 0 156px;"));
        assertTrue(profileSectionBlock.contains("min-height: 0;"));
        assertTrue(profileSectionBlock.contains("overflow: hidden;"));
        assertTrue(profileListBlock.contains("min-height: 0;"));
        assertTrue(profileListBlock.contains("overflow: auto;"));
    }

    private String cssBlock(String template, String selector) {
        int start = template.indexOf(selector + " {");
        int end = template.indexOf("\n        }", start);
        return template.substring(start, end);
    }
}
