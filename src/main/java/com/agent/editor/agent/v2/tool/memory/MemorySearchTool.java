package com.agent.editor.agent.v2.tool.memory;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolHandler;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import com.agent.editor.model.RetrievedLongTermMemory;
import com.agent.editor.service.LongTermMemoryRetrievalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.util.List;

public class MemorySearchTool implements ToolHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final String NAME = "memory_search";

    private final LongTermMemoryRetrievalService retrievalService;

    public MemorySearchTool(LongTermMemoryRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description("Search confirmed prior document decisions when continuing earlier work or avoiding rejected directions")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("query", "Natural language query for prior document decisions")
                        .addStringProperty("documentId", "Optional document scope for the decision search")
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
                    arguments.getDocumentId(),
                    arguments.getTopK()
            );
            return new ToolResult(OBJECT_MAPPER.writeValueAsString(memories));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to serialize tool result for " + name(), exception);
        }
    }

    private MemorySearchArguments decodeArguments(String arguments) {
        try {
            return OBJECT_MAPPER.readValue(arguments, MemorySearchArguments.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse tool arguments for " + name(), exception);
        }
    }
}
