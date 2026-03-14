package com.agent.editor.agent.v2.tool.document;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolHandler;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

public class EditDocumentTool implements ToolHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "editDocument";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description("Edit the document content with specified changes")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("content", "The full updated document content")
                        .required("content")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(ToolInvocation invocation, ToolContext context) {
        try {
            JsonNode arguments = objectMapper.readTree(invocation.arguments());
            String content = arguments.path("content").asText("");
            if (content.isEmpty()) {
                return new ToolResult("No content provided to edit the document.");
            }
            return new ToolResult("Document content edited successfully.", content);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse tool arguments for editDocument", exception);
        }
    }
}
