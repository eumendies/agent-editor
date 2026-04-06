package com.agent.editor.agent.v2.tool.memory;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import com.agent.editor.service.LongTermMemoryWriteService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryUpsertToolTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldReturnPerformedActionAndMemoryIdAsJson() {
        LongTermMemoryWriteService writeService = mock(LongTermMemoryWriteService.class);
        when(writeService.upsertResult(
                MemoryUpsertAction.REPLACE,
                "DOCUMENT_DECISION",
                "memory-2",
                "doc-1",
                "Keep the current outline"
        )).thenReturn(new MemoryUpsertResult(
                "REPLACE",
                "memory-2",
                "DOCUMENT_DECISION",
                "doc-1",
                "Keep the current outline"
        ));
        MemoryUpsertTool tool = new MemoryUpsertTool(writeService);

        ToolResult result = tool.execute(
                new ToolInvocation(MemoryToolNames.UPSERT_MEMORY, """
                        {"action":"REPLACE","memoryType":"DOCUMENT_DECISION","memoryId":"memory-2","documentId":"doc-1","summary":"Keep the current outline"}
                        """),
                new ToolContext("task-1", "doc-1", "title", "body")
        );

        assertTrue(tool.specification().name().equals(MemoryToolNames.UPSERT_MEMORY));
        assertTrue(result.getMessage().contains("\"action\":\"REPLACE\""));
        assertTrue(result.getMessage().contains("\"memoryId\":\"memory-2\""));
        assertTrue(result.getMessage().contains("\"memoryType\":\"DOCUMENT_DECISION\""));
        assertTrue(result.getMessage().contains("\"summary\":\"Keep the current outline\""));
    }

    @Test
    void shouldReturnJsonErrorWhenWriteServiceRejectsTheRequest() throws Exception {
        LongTermMemoryWriteService writeService = mock(LongTermMemoryWriteService.class);
        when(writeService.upsertResult(
                MemoryUpsertAction.REPLACE,
                "DOCUMENT_DECISION",
                null,
                "doc-1",
                "Keep the current outline"
        )).thenThrow(new IllegalArgumentException("memoryId is required for replace/delete"));
        MemoryUpsertTool tool = new MemoryUpsertTool(writeService);

        ToolResult result = tool.execute(
                new ToolInvocation(MemoryToolNames.UPSERT_MEMORY, """
                        {"action":"REPLACE","memoryType":"DOCUMENT_DECISION","documentId":"doc-1","summary":"Keep the current outline"}
                        """),
                new ToolContext("task-1", "doc-1", "title", "body")
        );

        JsonNode payload = OBJECT_MAPPER.readTree(result.getMessage());
        assertEquals("error", payload.get("status").asText());
        assertEquals("memoryId is required for replace/delete", payload.get("errorMessage").asText());
        assertEquals("REPLACE", payload.get("action").asText());
        assertEquals("DOCUMENT_DECISION", payload.get("memoryType").asText());
        assertTrue(payload.get("memoryId").isNull());
        assertEquals("doc-1", payload.get("documentId").asText());
        assertNull(result.getUpdatedContent());
    }

    @Test
    void shouldReturnJsonErrorWhenActionIsInvalid() throws Exception {
        LongTermMemoryWriteService writeService = mock(LongTermMemoryWriteService.class);
        MemoryUpsertTool tool = new MemoryUpsertTool(writeService);

        ToolResult result = tool.execute(
                new ToolInvocation(MemoryToolNames.UPSERT_MEMORY, """
                        {"action":"UPSERT","memoryType":"USER_PROFILE","summary":"Prefer concise summaries"}
                        """),
                new ToolContext("task-1", "doc-1", "title", "body")
        );

        JsonNode payload = OBJECT_MAPPER.readTree(result.getMessage());
        assertEquals("error", payload.get("status").asText());
        assertEquals("No enum constant com.agent.editor.agent.v2.tool.memory.MemoryUpsertAction.UPSERT", payload.get("errorMessage").asText());
        assertEquals("UPSERT", payload.get("action").asText());
        assertEquals("USER_PROFILE", payload.get("memoryType").asText());
        assertNull(result.getUpdatedContent());
    }

    @Test
    void shouldRejectUserProfileWritesFromMemoryWorker() throws Exception {
        LongTermMemoryWriteService writeService = mock(LongTermMemoryWriteService.class);
        MemoryUpsertTool tool = new MemoryUpsertTool(writeService);

        ToolResult result = tool.execute(
                new ToolInvocation(MemoryToolNames.UPSERT_MEMORY, """
                        {"action":"CREATE","memoryType":"USER_PROFILE","summary":"Prefer concise summaries"}
                        """),
                new ToolContext("task-1", "doc-1", "title", "body")
        );

        JsonNode payload = OBJECT_MAPPER.readTree(result.getMessage());
        assertEquals("error", payload.get("status").asText());
        assertEquals("Autonomous memory writes may only target DOCUMENT_DECISION", payload.get("errorMessage").asText());
    }
}
