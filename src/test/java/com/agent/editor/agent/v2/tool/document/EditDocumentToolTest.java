package com.agent.editor.agent.v2.tool.document;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EditDocumentToolTest {

    @Test
    void shouldReplaceCurrentDocumentContent() {
        EditDocumentTool tool = new EditDocumentTool();

        ToolResult result = tool.execute(
                new ToolInvocation("editDocument", "{\"content\":\"rewritten\"}"),
                new ToolContext("task-1", "original")
        );

        assertEquals("Document content edited successfully.", result.getMessage());
        assertEquals("rewritten", result.getUpdatedContent());
        assertEquals("editDocument", tool.specification().name());
    }

    @Test
    void shouldReturnMessageWhenTypedArgumentsDoNotContainContent() {
        EditDocumentTool tool = new EditDocumentTool();

        ToolResult result = tool.execute(
                new ToolInvocation("editDocument", "{}"),
                new ToolContext("task-1", "original")
        );

        assertEquals("No content provided to edit the document.", result.getMessage());
        assertEquals(null, result.getUpdatedContent());
    }

    @Test
    void shouldFailWhenArgumentsAreNotValidJson() {
        EditDocumentTool tool = new EditDocumentTool();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> tool.execute(
                new ToolInvocation("editDocument", "{not-json}"),
                new ToolContext("task-1", "original")
        ));

        assertEquals("Failed to parse tool arguments for editDocument", exception.getMessage());
    }
}
