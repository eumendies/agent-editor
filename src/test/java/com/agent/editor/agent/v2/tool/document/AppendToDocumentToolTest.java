package com.agent.editor.agent.v2.tool.document;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppendToDocumentToolTest {

    @Test
    void shouldAppendRawContentToCurrentDocumentEnd() {
        AppendToDocumentTool tool = new AppendToDocumentTool();

        ToolResult result = tool.execute(
                new ToolInvocation("appendToDocument", "{\"content\":\"\\nmore\"}"),
                new ToolContext("task-1", "original")
        );

        assertEquals("Document content appended successfully.", result.getMessage());
        assertEquals("original\nmore", result.getUpdatedContent());
        assertEquals("appendToDocument", tool.specification().name());
    }

    @Test
    void shouldTreatNullCurrentContentAsEmptyWhenAppending() {
        AppendToDocumentTool tool = new AppendToDocumentTool();

        ToolResult result = tool.execute(
                new ToolInvocation("appendToDocument", "{\"content\":\"hello\"}"),
                new ToolContext("task-1", null)
        );

        assertEquals("hello", result.getUpdatedContent());
    }

    @Test
    void shouldReturnMessageWhenAppendContentMissing() {
        AppendToDocumentTool tool = new AppendToDocumentTool();

        ToolResult result = tool.execute(
                new ToolInvocation("appendToDocument", "{}"),
                new ToolContext("task-1", "original")
        );

        assertEquals("No content provided to append to the document.", result.getMessage());
        assertNull(result.getUpdatedContent());
    }

    @Test
    void shouldFailWhenArgumentsAreNotValidJson() {
        AppendToDocumentTool tool = new AppendToDocumentTool();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> tool.execute(
                new ToolInvocation("appendToDocument", "{not-json}"),
                new ToolContext("task-1", "original")
        ));

        assertEquals("Failed to parse tool arguments for appendToDocument", exception.getMessage());
    }
}
