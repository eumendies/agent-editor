package com.agent.editor.agent.v2.tool.memory;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolHandler;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.RecoverableToolException;
import com.agent.editor.agent.v2.tool.ToolResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agent.editor.service.LongTermMemoryWriteService;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

/**
 * 长期记忆写入工具。
 * 运行时是否允许模型看到这个工具，由外层的 tool access policy 决定；这里仅兜底约束可写入的 memory 类型。
 */
public class MemoryUpsertTool implements ToolHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LongTermMemoryWriteService writeService;

    public MemoryUpsertTool(LongTermMemoryWriteService writeService) {
        this.writeService = writeService;
    }

    @Override
    public String name() {
        return MemoryToolNames.UPSERT_MEMORY;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description("Create, replace, or delete long-term memory only when the user clearly states a durable preference or explicitly corrects prior memory")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("action", "One of CREATE, REPLACE, DELETE")
                        .addStringProperty("memoryType", "One of USER_PROFILE or DOCUMENT_DECISION")
                        .addStringProperty("memoryId", "Existing memory id for replace/delete")
                        .addStringProperty("documentId", "Required when creating document decisions")
                        .addStringProperty("summary", "Required for create/replace")
                        .required("action", "memoryType")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(ToolInvocation invocation, ToolContext context) {
        MemoryUpsertArguments arguments = decodeArguments(invocation.getArguments());
        try {
            MemoryUpsertAction action = MemoryUpsertAction.valueOf(arguments.getAction());
            validateAutonomousWrite(arguments);
            MemoryUpsertResult result = writeService.upsertResult(
                    action,
                    arguments.getMemoryType(),
                    arguments.getMemoryId(),
                    arguments.getDocumentId(),
                    arguments.getSummary()
            );
            return new ToolResult(serialize(result));
        } catch (IllegalArgumentException exception) {
            // memory 写入失败通常是模型参数问题，改成可恢复异常让 runtime 统一反馈给模型。
            throw new RecoverableToolException(exception.getMessage(), exception);
        }
    }

    /**
     * AI 自动维护长期记忆时，只允许写文档决策类约束，避免污染用户画像。
     */
    private void validateAutonomousWrite(MemoryUpsertArguments arguments) {
        if (!"DOCUMENT_DECISION".equals(arguments.getMemoryType())) {
            throw new IllegalArgumentException("Autonomous memory writes may only target DOCUMENT_DECISION");
        }
    }

    private MemoryUpsertArguments decodeArguments(String arguments) {
        try {
            return OBJECT_MAPPER.readValue(arguments, MemoryUpsertArguments.class);
        } catch (Exception exception) {
            throw new RecoverableToolException("Failed to parse tool arguments for " + name(), exception);
        }
    }

    private String serialize(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize result for " + name(), exception);
        }
    }
}
