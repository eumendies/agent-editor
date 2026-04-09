package com.agent.editor.agent.v2.tool.document;

import com.agent.editor.agent.v2.tool.RecoverableToolException;
import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchContentToolTest {

    @Test
    void shouldSearchWithinCurrentContent() {
        SearchContentTool tool = new SearchContentTool();

        ToolResult result = tool.execute(
                new ToolInvocation(DocumentToolNames.SEARCH_CONTENT, "{\"pattern\":\"agent\"}"),
                new ToolContext("task-1", "hello agent")
        );

        assertEquals("Search for 'agent': Found", result.getMessage());
        assertEquals(null, result.getUpdatedContent());
    }

    @Test
    void shouldReturnNotFoundWhenTypedArgumentsDoNotContainPattern() {
        SearchContentTool tool = new SearchContentTool();

        ToolResult result = tool.execute(
                new ToolInvocation(DocumentToolNames.SEARCH_CONTENT, "{}"),
                new ToolContext("task-1", "hello agent")
        );

        assertEquals("Search for '': Not found", result.getMessage());
        assertEquals(null, result.getUpdatedContent());
    }

    @Test
    void shouldFailWhenArgumentsAreNotValidJson() {
        SearchContentTool tool = new SearchContentTool();

        RecoverableToolException exception = assertThrows(RecoverableToolException.class, () -> tool.execute(
                new ToolInvocation(DocumentToolNames.SEARCH_CONTENT, "{not-json}"),
                new ToolContext("task-1", "hello agent")
        ));

        assertEquals("Failed to parse tool arguments for " + DocumentToolNames.SEARCH_CONTENT, exception.getMessage());
    }
}
