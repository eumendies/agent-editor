package com.agent.editor.agent.v2.tool.document;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolHandler;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

public class AnalyzeDocumentTool implements ToolHandler {

    @Override
    public String name() {
        return DocumentToolNames.ANALYZE_DOCUMENT;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description("Analyze the current document for word count, line count, and character count")
                .parameters(JsonObjectSchema.builder().build())
                .build();
    }

    @Override
    public ToolResult execute(ToolInvocation invocation, ToolContext context) {
        String content = context.getCurrentContent() == null ? "" : context.getCurrentContent();
        int words = content.isBlank() ? 0 : content.trim().split("\\s+").length;
        int lines = content.isEmpty() ? 0 : content.split("\n", -1).length;
        return new ToolResult("Words: " + words + ", Lines: " + lines + ", Chars: " + content.length());
    }
}
