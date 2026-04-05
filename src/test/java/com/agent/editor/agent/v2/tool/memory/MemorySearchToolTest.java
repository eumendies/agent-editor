package com.agent.editor.agent.v2.tool.memory;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import com.agent.editor.model.RetrievedLongTermMemory;
import com.agent.editor.service.LongTermMemoryRetrievalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemorySearchToolTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
                new ToolInvocation(MemoryToolNames.SEARCH_MEMORY, "{\"query\":\"previous choices\",\"documentId\":\"doc-1\",\"topK\":2}"),
                new ToolContext("task-1", null)
        );

        assertTrue(tool.specification().name().equals(MemoryToolNames.SEARCH_MEMORY));
        assertTrue(result.getMessage().contains("\"memoryId\":\"memory-2\""));
        assertTrue(result.getMessage().contains("\"summary\":\"Keep section 3 unchanged\""));
        assertTrue(result.getMessage().contains("\"relevanceReason\":\"Matched the current editing direction for doc-1\""));
        assertFalse(result.getMessage().contains("\"details\""));
        assertFalse(result.getMessage().contains("\"scopeKey\""));
    }

    @Test
    void shouldReturnJsonErrorWhenRetrievalServiceRejectsTheRequest() throws Exception {
        LongTermMemoryRetrievalService retrievalService = mock(LongTermMemoryRetrievalService.class);
        when(retrievalService.searchConfirmedDocumentDecisions("previous choices", "doc-1", 2))
                .thenThrow(new IllegalArgumentException("topK must be positive"));
        MemorySearchTool tool = new MemorySearchTool(retrievalService);

        ToolResult result = tool.execute(
                new ToolInvocation(MemoryToolNames.SEARCH_MEMORY, "{\"query\":\"previous choices\",\"documentId\":\"doc-1\",\"topK\":2}"),
                new ToolContext("task-1", null)
        );

        JsonNode payload = OBJECT_MAPPER.readTree(result.getMessage());
        assertEquals("error", payload.get("status").asText());
        assertEquals("topK must be positive", payload.get("errorMessage").asText());
        assertEquals("previous choices", payload.get("query").asText());
        assertEquals("doc-1", payload.get("documentId").asText());
        assertEquals(2, payload.get("topK").asInt());
        assertNull(result.getUpdatedContent());
    }
}
