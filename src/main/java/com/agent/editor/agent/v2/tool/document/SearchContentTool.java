package com.agent.editor.agent.v2.tool.document;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolHandler;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

public class SearchContentTool implements ToolHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "searchContent";
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
        try {
            JsonNode arguments = objectMapper.readTree(invocation.arguments());
            String pattern = arguments.path("pattern").asText("");
            String content = context.currentContent() == null ? "" : context.currentContent();
            boolean found = !pattern.isEmpty() && content.toLowerCase().contains(pattern.toLowerCase());
            return new ToolResult("Search for '" + pattern + "': " + (found ? "Found" : "Not found"));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse tool arguments for searchContent", exception);
        }
    }
}
