package com.agent.editor.agent.tool.document;

import com.agent.editor.agent.tool.ToolContext;
import com.agent.editor.agent.tool.ToolHandler;
import com.agent.editor.agent.tool.ToolInvocation;
import com.agent.editor.agent.tool.ToolResult;
import com.agent.editor.model.EvidenceChunk;
import com.agent.editor.service.KnowledgeRetrievalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.util.List;

public class RetrieveKnowledgeTool implements ToolHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final KnowledgeRetrievalService retrievalService;

    public RetrieveKnowledgeTool(KnowledgeRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Override
    public String name() {
        return DocumentToolNames.RETRIEVE_KNOWLEDGE;
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
                invocation.getArguments(),
                RetrieveKnowledgeArguments.class,
                name()
        );
        try {
            // researcher 只需要判断证据是否充分，不需要看到内部检索定位和打分字段。
            List<EvidenceChunk> chunks = retrievalService.retrieve(arguments.getQuery(), arguments.getDocumentIds(), arguments.getTopK())
                    .stream()
                    .map(EvidenceChunk::fromRetrieved)
                    .toList();
            return new ToolResult(OBJECT_MAPPER.writeValueAsString(
                    chunks
            ));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to serialize tool result for " + name(), exception);
        }
    }
}
