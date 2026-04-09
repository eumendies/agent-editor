package com.agent.editor.agent.tool.document;

import com.agent.editor.agent.tool.ToolContext;
import com.agent.editor.agent.tool.ToolHandler;
import com.agent.editor.agent.tool.ToolInvocation;
import com.agent.editor.agent.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

public class EditDocumentTool implements ToolHandler {

    @Override
    public String name() {
        return DocumentToolNames.EDIT_DOCUMENT;
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
        EditDocumentArguments arguments = ToolArgumentDecoder.decode(invocation.getArguments(), EditDocumentArguments.class, name());
        String content = arguments.getContent();
        if (content == null || content.isEmpty()) {
            return new ToolResult("No content provided to edit the document.");
        }
        return new ToolResult("Document content edited successfully.", content);
    }
}
