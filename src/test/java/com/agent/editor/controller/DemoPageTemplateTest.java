package com.agent.editor.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoPageTemplateTest {

    @Test
    void shouldExposeOrchestrationDemoCopy() throws IOException {
        String template = Files.readString(Path.of("src/main/resources/templates/index.html"));

        assertTrue(template.contains("Agent V2 Orchestration Demo"));
        assertTrue(template.contains("Run ReAct"));
        assertTrue(template.contains("Run Planning"));
        assertTrue(template.contains("Run Supervisor"));
        assertTrue(template.contains("Run Reflexion"));
        assertTrue(template.contains("Trace Inspector"));
        assertTrue(template.contains("tracePanel"));
        assertTrue(template.contains("Knowledge Base Upload"));
        assertTrue(template.contains("knowledgeUploadForm"));
        assertTrue(template.contains("knowledgeFileInput"));
        assertTrue(template.contains("knowledgeCategoryInput"));
        assertTrue(template.contains("knowledgeUploadResult"));
    }
}
