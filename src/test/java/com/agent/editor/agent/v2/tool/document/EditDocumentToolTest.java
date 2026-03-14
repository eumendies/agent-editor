package com.agent.editor.agent.v2.tool.document;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditDocumentToolTest {

    @Test
    void shouldReplaceCurrentDocumentContent() {
        EditDocumentTool tool = new EditDocumentTool();

        ToolResult result = tool.execute(
                new ToolInvocation("editDocument", "{\"content\":\"rewritten\"}"),
                new ToolContext("task-1", "original")
        );

        assertEquals("Document content edited successfully.", result.message());
        assertEquals("rewritten", result.updatedContent());
        assertEquals("editDocument", tool.specification().name());
    }
}
