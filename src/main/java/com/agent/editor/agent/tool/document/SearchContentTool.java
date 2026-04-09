package com.agent.editor.agent.tool.document;

import com.agent.editor.agent.tool.ToolContext;
import com.agent.editor.agent.tool.ToolHandler;
import com.agent.editor.agent.tool.ToolInvocation;
import com.agent.editor.agent.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

public class SearchContentTool implements ToolHandler {

    @Override
    public String name() {
        return DocumentToolNames.SEARCH_CONTENT;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description("Search for specific text in the current document")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("pattern", "The text to search for")
                        .required("pattern")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(ToolInvocation invocation, ToolContext context) {
        SearchContentArguments arguments = ToolArgumentDecoder.decode(invocation.getArguments(), SearchContentArguments.class, name());
        String pattern = arguments.getPattern() == null ? "" : arguments.getPattern();
        String content = context.getCurrentContent() == null ? "" : context.getCurrentContent();
        boolean found = !pattern.isEmpty() && content.toLowerCase().contains(pattern.toLowerCase());
        return new ToolResult("Search for '" + pattern + "': " + (found ? "Found" : "Not found"));
    }
}
