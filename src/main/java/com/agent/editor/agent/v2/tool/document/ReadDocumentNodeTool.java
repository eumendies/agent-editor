package com.agent.editor.agent.v2.tool.document;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolHandler;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import com.agent.editor.agent.v2.tool.RecoverableToolException;
import com.agent.editor.service.StructuredDocumentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class ReadDocumentNodeTool implements ToolHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StructuredDocumentService structuredDocumentService;

    public ReadDocumentNodeTool(StructuredDocumentService structuredDocumentService) {
        this.structuredDocumentService = structuredDocumentService;
    }

    @Override
    public String name() {
        return DocumentToolNames.READ_DOCUMENT_NODE;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description("Read document structure, node content, or oversized leaf blocks without returning the full document")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("nodeId", "Stable node id from the structure snapshot")
                        .addStringProperty("mode", "One of structure, content, or blocks")
                        .addStringProperty("blockId", "Specific oversized leaf block id when reading one block")
                        .addProperty("includeChildren", JsonBooleanSchema.builder().description("Whether child summaries should be included").build())
                        .required("nodeId", "mode")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(ToolInvocation invocation, ToolContext context) {
        ReadDocumentNodeArguments arguments = ToolArgumentDecoder.decode(invocation.getArguments(), ReadDocumentNodeArguments.class, name());
        try {
            StructuredDocumentService.NodeReadResult result = structuredDocumentService.readNode(
                    "",
                    context.getCurrentContent(),
                    arguments.getNodeId(),
                    arguments.getMode(),
                    arguments.getBlockId(),
                    Boolean.TRUE.equals(arguments.getIncludeChildren())
            );
            return new ToolResult(serialize(result));
        } catch (IllegalArgumentException exception) {
            // 这里把文档读取校验错误显式标成“模型可修复”，由 runtime 统一转成 tool result。
            throw new RecoverableToolException(exception.getMessage(), exception);
        }
    }

    private String serialize(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize result for " + name(), e);
        }
    }

}
