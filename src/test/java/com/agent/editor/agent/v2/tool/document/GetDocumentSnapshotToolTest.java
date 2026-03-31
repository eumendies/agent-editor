package com.agent.editor.agent.v2.tool.document;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetDocumentSnapshotToolTest {

    @Test
    void shouldReturnCurrentDocumentSnapshotWithoutMutatingContent() {
        GetDocumentSnapshotTool tool = new GetDocumentSnapshotTool();

        ToolResult result = tool.execute(
                new ToolInvocation(DocumentToolNames.GET_DOCUMENT_SNAPSHOT, "{}"),
                new ToolContext("task-1", "latest body")
        );

        assertEquals("latest body", result.getMessage());
        assertNull(result.getUpdatedContent());
        assertEquals(DocumentToolNames.GET_DOCUMENT_SNAPSHOT, tool.specification().name());
    }

    @Test
    void shouldReturnEmptySnapshotWhenCurrentContentMissing() {
        GetDocumentSnapshotTool tool = new GetDocumentSnapshotTool();

        ToolResult result = tool.execute(
                new ToolInvocation(DocumentToolNames.GET_DOCUMENT_SNAPSHOT, "{}"),
                new ToolContext("task-1", null)
        );

        assertEquals("", result.getMessage());
        assertNull(result.getUpdatedContent());
    }

    @Test
    void shouldFailWhenArgumentsAreNotValidJson() {
        GetDocumentSnapshotTool tool = new GetDocumentSnapshotTool();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> tool.execute(
                new ToolInvocation(DocumentToolNames.GET_DOCUMENT_SNAPSHOT, "{not-json}"),
                new ToolContext("task-1", "body")
        ));

        assertEquals("Failed to parse tool arguments for " + DocumentToolNames.GET_DOCUMENT_SNAPSHOT, exception.getMessage());
    }
}
