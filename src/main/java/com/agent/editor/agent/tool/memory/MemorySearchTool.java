package com.agent.editor.agent.tool.memory;

import com.agent.editor.agent.tool.ToolContext;
import com.agent.editor.agent.tool.ToolHandler;
import com.agent.editor.agent.tool.ToolInvocation;
import com.agent.editor.agent.tool.RecoverableToolException;
import com.agent.editor.agent.tool.ToolResult;
import com.agent.editor.model.RetrievedLongTermMemory;
import com.agent.editor.service.LongTermMemoryRetrievalService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.util.List;

public class MemorySearchTool implements ToolHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LongTermMemoryRetrievalService retrievalService;

    public MemorySearchTool(LongTermMemoryRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Override
    public String name() {
        return MemoryToolNames.SEARCH_MEMORY;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description("Search confirmed prior document decisions when continuing earlier work or avoiding rejected directions")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("query", "Natural language query for prior document decisions")
                        .addIntegerProperty("topK", "Optional maximum number of memories to return")
                        .required("query")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(ToolInvocation invocation, ToolContext context) {
        MemorySearchArguments arguments = decodeArguments(invocation.getArguments());
        try {
            List<RetrievedLongTermMemory> memories = retrievalService.searchConfirmedDocumentDecisions(
                    arguments.getQuery(),
                    resolveDocumentId(arguments, context),
                    arguments.getTopK()
            );
            return new ToolResult(serialize(memories));
        } catch (IllegalArgumentException exception) {
            // 记忆检索参数多数来自模型推断，需作为可恢复错误回注，而不是中断整个 loop。
            throw new RecoverableToolException(exception.getMessage(), exception);
        }
    }

    private MemorySearchArguments decodeArguments(String arguments) {
        try {
            return OBJECT_MAPPER.readValue(arguments, MemorySearchArguments.class);
        } catch (Exception exception) {
            throw new RecoverableToolException("Failed to parse tool arguments for " + name(), exception);
        }
    }

    private String resolveDocumentId(MemorySearchArguments arguments, ToolContext context) {
        String contextDocumentId = context == null ? null : context.getDocumentId();
        if (contextDocumentId != null && !contextDocumentId.isBlank()) {
            // 当前文档范围由 runtime 提供；参数里的 documentId 只作为旧调用点缺少上下文时的兼容兜底。
            return contextDocumentId;
        }
        return arguments.getDocumentId();
    }

    private String serialize(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize result for " + name(), exception);
        }
    }
}
