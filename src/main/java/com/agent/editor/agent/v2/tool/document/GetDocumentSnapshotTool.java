package com.agent.editor.agent.v2.tool.document;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolHandler;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

public class GetDocumentSnapshotTool implements ToolHandler {

    @Override
    public String name() {
        return DocumentToolNames.GET_DOCUMENT_SNAPSHOT;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description("Return the latest current document snapshot visible to this tool loop")
                .parameters(JsonObjectSchema.builder().build())
                .build();
    }

    @Override
    public ToolResult execute(ToolInvocation invocation, ToolContext context) {
        ToolArgumentDecoder.decode(invocation.getArguments(), GetDocumentSnapshotArguments.class, name());
        return new ToolResult(context.getCurrentContent() == null ? "" : context.getCurrentContent());
    }
}
