package com.agent.editor.agent.tool.memory;

import com.agent.editor.agent.tool.ToolContext;
import com.agent.editor.agent.tool.ToolInvocation;
import com.agent.editor.agent.tool.RecoverableToolException;
import com.agent.editor.agent.tool.ToolResult;
import com.agent.editor.service.LongTermMemoryWriteService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryUpsertToolTest {

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
    void shouldThrowRecoverableExceptionWhenWriteServiceRejectsTheRequest() {
        LongTermMemoryWriteService writeService = mock(LongTermMemoryWriteService.class);
        when(writeService.upsertResult(
                MemoryUpsertAction.REPLACE,
                "DOCUMENT_DECISION",
                null,
                "doc-1",
                "Keep the current outline"
        )).thenThrow(new IllegalArgumentException("memoryId is required for replace/delete"));
        MemoryUpsertTool tool = new MemoryUpsertTool(writeService);

        RecoverableToolException exception = assertThrows(RecoverableToolException.class, () -> tool.execute(
                new ToolInvocation(MemoryToolNames.UPSERT_MEMORY, """
                        {"action":"REPLACE","memoryType":"DOCUMENT_DECISION","documentId":"doc-1","summary":"Keep the current outline"}
                        """),
                new ToolContext("task-1", "doc-1", "title", "body")
        ));

        assertEquals("memoryId is required for replace/delete", exception.getMessage());
    }

    @Test
    void shouldThrowRecoverableExceptionWhenActionIsInvalid() {
        LongTermMemoryWriteService writeService = mock(LongTermMemoryWriteService.class);
        MemoryUpsertTool tool = new MemoryUpsertTool(writeService);

        RecoverableToolException exception = assertThrows(RecoverableToolException.class, () -> tool.execute(
                new ToolInvocation(MemoryToolNames.UPSERT_MEMORY, """
                        {"action":"UPSERT","memoryType":"USER_PROFILE","summary":"Prefer concise summaries"}
                        """),
                new ToolContext("task-1", "doc-1", "title", "body")
        ));

        assertEquals("No enum constant com.agent.editor.agent.tool.memory.MemoryUpsertAction.UPSERT", exception.getMessage());
    }

    @Test
    void shouldThrowRecoverableExceptionWhenUserProfileWritesAreRejected() {
        LongTermMemoryWriteService writeService = mock(LongTermMemoryWriteService.class);
        MemoryUpsertTool tool = new MemoryUpsertTool(writeService);

        RecoverableToolException exception = assertThrows(RecoverableToolException.class, () -> tool.execute(
                new ToolInvocation(MemoryToolNames.UPSERT_MEMORY, """
                        {"action":"CREATE","memoryType":"USER_PROFILE","summary":"Prefer concise summaries"}
                        """),
                new ToolContext("task-1", "doc-1", "title", "body")
        ));

        assertEquals("Autonomous memory writes may only target DOCUMENT_DECISION", exception.getMessage());
    }
}
