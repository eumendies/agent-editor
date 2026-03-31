package com.agent.editor.agent.v2.tool.document;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalyzeDocumentToolTest {

    @Test
    void shouldReportBasicDocumentStats() {
        AnalyzeDocumentTool tool = new AnalyzeDocumentTool();

        ToolResult result = tool.execute(
                new ToolInvocation(DocumentToolNames.ANALYZE_DOCUMENT, "{}"),
                new ToolContext("task-1", "one two\nthree")
        );

        assertEquals("Words: 3, Lines: 2, Chars: 13", result.getMessage());
        assertEquals(null, result.getUpdatedContent());
    }
}
