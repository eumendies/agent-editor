package com.agent.editor.agent.v2.tool.memory;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import com.agent.editor.model.RetrievedLongTermMemory;
import com.agent.editor.service.LongTermMemoryRetrievalService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemorySearchToolTest {

    @Test
    void shouldReturnCondensedTaskDecisionCardsAsJson() {
        LongTermMemoryRetrievalService retrievalService = mock(LongTermMemoryRetrievalService.class);
        when(retrievalService.searchConfirmedDocumentDecisions("previous choices", "doc-1", 2))
                .thenReturn(List.of(new RetrievedLongTermMemory(
                        "memory-2",
                        "DOCUMENT_DECISION",
                        "Keep section 3 unchanged",
                        "Matched the current editing direction for doc-1",
                        "task-1",
                        "2026-04-03T10:00:00"
                )));
        MemorySearchTool tool = new MemorySearchTool(retrievalService);

        ToolResult result = tool.execute(
                new ToolInvocation("memory_search", "{\"query\":\"previous choices\",\"documentId\":\"doc-1\",\"topK\":2}"),
                new ToolContext("task-1", null)
        );

        assertTrue(result.getMessage().contains("\"memoryId\":\"memory-2\""));
        assertTrue(result.getMessage().contains("\"summary\":\"Keep section 3 unchanged\""));
        assertTrue(result.getMessage().contains("\"relevanceReason\":\"Matched the current editing direction for doc-1\""));
        assertFalse(result.getMessage().contains("\"details\""));
        assertFalse(result.getMessage().contains("\"scopeKey\""));
    }
}
