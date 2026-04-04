package com.agent.editor.agent.v2.tool.memory;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolHandler;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agent.editor.service.LongTermMemoryWriteService;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

public class MemoryUpsertTool implements ToolHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final String NAME = "memory_upsert";

    private final LongTermMemoryWriteService writeService;

    public MemoryUpsertTool(LongTermMemoryWriteService writeService) {
        this.writeService = writeService;
    }

    @Override
    public String name() {
        return NAME;
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
            MemoryUpsertResult result = writeService.upsertResult(
                    MemoryUpsertAction.valueOf(arguments.getAction()),
                    arguments.getMemoryType(),
                    arguments.getMemoryId(),
                    arguments.getDocumentId(),
                    arguments.getSummary()
            );
            return new ToolResult(OBJECT_MAPPER.writeValueAsString(result));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to execute tool " + name(), exception);
        }
    }

    private MemoryUpsertArguments decodeArguments(String arguments) {
        try {
            return OBJECT_MAPPER.readValue(arguments, MemoryUpsertArguments.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse tool arguments for " + name(), exception);
        }
    }
}
