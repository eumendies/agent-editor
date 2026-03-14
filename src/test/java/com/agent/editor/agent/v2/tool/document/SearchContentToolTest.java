package com.agent.editor.agent.v2.tool.document;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchContentToolTest {

    @Test
    void shouldSearchWithinCurrentContent() {
        SearchContentTool tool = new SearchContentTool();

        ToolResult result = tool.execute(
                new ToolInvocation("searchContent", "{\"pattern\":\"agent\"}"),
                new ToolContext("task-1", "hello agent")
        );

        assertEquals("Search for 'agent': Found", result.message());
        assertEquals(null, result.updatedContent());
    }
}
