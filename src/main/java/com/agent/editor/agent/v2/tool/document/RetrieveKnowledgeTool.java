package com.agent.editor.agent.v2.tool.document;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolHandler;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import com.agent.editor.service.KnowledgeRetrievalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

public class RetrieveKnowledgeTool implements ToolHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final KnowledgeRetrievalService retrievalService;

    public RetrieveKnowledgeTool(KnowledgeRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Override
    public String name() {
        return "retrieveKnowledge";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description("Retrieve relevant knowledge chunks for a natural language query")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("query", "Natural language retrieval query")
                        .addIntegerProperty("topK", "Optional maximum number of chunks to return")
                        .required("query")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(ToolInvocation invocation, ToolContext context) {
        RetrieveKnowledgeArguments arguments = ToolArgumentDecoder.decode(
                invocation.arguments(),
                RetrieveKnowledgeArguments.class,
                name()
        );
        try {
            return new ToolResult(OBJECT_MAPPER.writeValueAsString(
                    retrievalService.retrieve(arguments.query(), arguments.documentIds(), arguments.topK())
            ));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to serialize tool result for " + name(), exception);
        }
    }
}
