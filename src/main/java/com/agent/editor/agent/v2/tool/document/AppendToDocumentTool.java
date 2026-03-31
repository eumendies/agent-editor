package com.agent.editor.agent.v2.tool.document;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolHandler;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

public class AppendToDocumentTool implements ToolHandler {

    @Override
    public String name() {
        return DocumentToolNames.APPEND_TO_DOCUMENT;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description("Append raw content to the end of the current document without overwriting existing content")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("content", "The raw text to append to the end of the current document")
                        .required("content")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(ToolInvocation invocation, ToolContext context) {
        AppendToDocumentArguments arguments = ToolArgumentDecoder.decode(invocation.getArguments(), AppendToDocumentArguments.class, name());
        String content = arguments.getContent();
        if (content == null || content.isEmpty()) {
            return new ToolResult("No content provided to append to the document.");
        }
        String currentContent = context.getCurrentContent() == null ? "" : context.getCurrentContent();
        return new ToolResult("Document content appended successfully.", currentContent + content);
    }
}
