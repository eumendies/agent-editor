package com.agent.editor.agent.v2.tool.memory;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import com.agent.editor.service.LongTermMemoryWriteService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryUpsertToolTest {

    @Test
    void shouldReturnPerformedActionAndMemoryIdAsJson() {
        LongTermMemoryWriteService writeService = mock(LongTermMemoryWriteService.class);
        when(writeService.upsertResult(
                MemoryUpsertAction.REPLACE,
                "USER_PROFILE",
                "memory-2",
                null,
                "Prefer concise summaries"
        )).thenReturn(new MemoryUpsertResult(
                "REPLACE",
                "memory-2",
                "USER_PROFILE",
                null,
                "Prefer concise summaries"
        ));
        MemoryUpsertTool tool = new MemoryUpsertTool(writeService);

        ToolResult result = tool.execute(
                new ToolInvocation(MemoryToolNames.UPSERT_MEMORY, """
                        {"action":"REPLACE","memoryType":"USER_PROFILE","memoryId":"memory-2","summary":"Prefer concise summaries"}
                        """),
                new ToolContext("task-1", "doc-1")
        );

        assertTrue(tool.specification().name().equals(MemoryToolNames.UPSERT_MEMORY));
        assertTrue(result.getMessage().contains("\"action\":\"REPLACE\""));
        assertTrue(result.getMessage().contains("\"memoryId\":\"memory-2\""));
        assertTrue(result.getMessage().contains("\"memoryType\":\"USER_PROFILE\""));
        assertTrue(result.getMessage().contains("\"summary\":\"Prefer concise summaries\""));
    }
}
