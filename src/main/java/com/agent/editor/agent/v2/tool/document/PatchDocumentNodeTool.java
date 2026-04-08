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
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class PatchDocumentNodeTool implements ToolHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StructuredDocumentService structuredDocumentService;

    public PatchDocumentNodeTool(StructuredDocumentService structuredDocumentService) {
        this.structuredDocumentService = structuredDocumentService;
    }

    @Override
    public String name() {
        return DocumentToolNames.PATCH_DOCUMENT_NODE;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description("Apply an incremental patch to a document node or oversized leaf block")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("documentVersion", "The document version used as the patch baseline")
                        .addStringProperty("nodeId", "Stable node id from the structure snapshot")
                        .addStringProperty("blockId", "Optional block id when patching an oversized leaf block")
                        .addStringProperty("baseHash", "Hash of the node or block content used as the patch baseline")
                        .addStringProperty("operation", "One of replace_node or replace_block")
                        .addStringProperty("content", "Replacement content for the target node or block")
                        .addStringProperty("reason", "Optional reason for the patch")
                        .required("documentVersion", "nodeId", "baseHash", "operation", "content")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(ToolInvocation invocation, ToolContext context) {
        PatchDocumentNodeArguments arguments = ToolArgumentDecoder.decode(invocation.getArguments(), PatchDocumentNodeArguments.class, name());
        try {
            StructuredDocumentService.PatchResult result = structuredDocumentService.applyPatch(
                    "",
                    context.getCurrentContent(),
                    new StructuredDocumentService.PatchRequest(
                            arguments.getDocumentVersion(),
                            arguments.getNodeId(),
                            arguments.getBlockId(),
                            arguments.getBaseHash(),
                            arguments.getOperation(),
                            arguments.getContent(),
                            arguments.getReason()
                    )
            );
            return new ToolResult(
                    serialize(new PatchToolResponse(
                            result.getStatus(),
                            result.getDocumentVersion(),
                            arguments.getNodeId(),
                            arguments.getBlockId(),
                            arguments.getOperation()
                    )),
                    result.getUpdatedContent()
            );
        } catch (IllegalArgumentException exception) {
            // patch 参数和基线错误通常是模型可修复输入，交给 runtime 统一回注给模型。
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class PatchToolResponse {

        private String status;
        private String documentVersion;
        private String nodeId;
        private String blockId;
        private String operation;
    }

}
