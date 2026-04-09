package com.agent.editor.agent.supervisor.worker;

import com.agent.editor.model.EvidenceChunk;
import com.agent.editor.agent.core.agent.*;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.context.ModelInvocationContext;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.memory.ObservedTokenUsageRecorder;
import com.agent.editor.agent.model.StreamingLLMInvoker;
import com.agent.editor.agent.tool.document.DocumentToolNames;
import com.agent.editor.agent.util.StructuredOutputParsers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * research worker。
 * 只负责拉取与整理证据，不直接改写文档正文。
 */
public class ResearcherAgent extends AbstractStreamingToolLoopAgent {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ResearcherAgentContextFactory contextFactory;

    public static ResearcherAgent blocking(ChatModel chatModel,
                                           ResearcherAgentContextFactory contextFactory) {
        return new ResearcherAgent(chatModel, null, contextFactory);
    }

    public static ResearcherAgent streaming(StreamingLLMInvoker streamingLLMInvoker,
                                            ResearcherAgentContextFactory contextFactory) {
        return new ResearcherAgent(null, streamingLLMInvoker, contextFactory);
    }

    private ResearcherAgent(ChatModel chatModel,
                            StreamingLLMInvoker streamingLLMInvoker,
                            ResearcherAgentContextFactory contextFactory) {
        super(chatModel, streamingLLMInvoker);
        this.contextFactory = contextFactory;
    }

    @Override
    public AgentType type() {
        return AgentType.REACT;
    }

    @Override
    public ToolLoopDecision decide(AgentRunContext context) {
        // 首轮先用用户原始 instruction 做一次固定召回，避免模型在没有证据前过早改写查询导致召回偏移。
        if (shouldRunInitialInstructionRetrieval(context)) {
            return new ToolLoopDecision.ToolCalls(
                    List.of(new ToolCall(DocumentToolNames.RETRIEVE_KNOWLEDGE, initialRetrieveKnowledgeArguments(context))),
                    "initial instruction retrieval"
            );
        }

        ModelInvocationContext invocationContext = contextFactory.buildModelInvocationContext(context);
        ChatResponse response = invokeModel(context, invocationContext);
        ObservedTokenUsageRecorder.record(context, response);

        AiMessage aiMessage = response.aiMessage();
        if (aiMessage.hasToolExecutionRequests()) {
            return new ToolLoopDecision.ToolCalls(
                    aiMessage.toolExecutionRequests().stream()
                            .map(this::toToolCall)
                            .toList(),
                    aiMessage.text()
            );
        }

        EvidencePackage evidencePackage = assembleEvidencePackage(context, aiMessage.text());
        if (evidencePackage != null) {
            return new ToolLoopDecision.Complete<>(evidencePackage, aiMessage.text());
        }
        return new ToolLoopDecision.Complete<>(aiMessage.text(), aiMessage.text());
    }
    private ToolCall toToolCall(ToolExecutionRequest request) {
        return new ToolCall(request.id(), request.name(), request.arguments());
    }

    private EvidencePackage assembleEvidencePackage(AgentRunContext context, String text) {
        ResearcherSummary summary = StructuredOutputParsers.parseJsonWithMarkdownCleanup(text, ResearcherSummary.class);
        if (summary == null) {
            return null;
        }
        LastRetrieveKnowledgeSnapshot snapshot = lastRetrieveKnowledgeSnapshot(context);
        // queries/chunks 以最后一次真实检索结果为准，避免模型伪造证据明细或回忆错查询轨迹。
        return new EvidencePackage(
                snapshot.queries(),
                summary.getEvidenceSummary(),
                summary.getLimitations(),
                defaultList(summary.getUncoveredPoints()),
                snapshot.chunks()
        );
    }

    private boolean shouldRunInitialInstructionRetrieval(AgentRunContext context) {
        if (!(context.state().getMemory() instanceof ChatTranscriptMemory transcriptMemory)) {
            return false;
        }
        return transcriptMemory.getMessages().stream()
                .filter(ChatMessage.ToolExecutionResultChatMessage.class::isInstance)
                .map(ChatMessage.ToolExecutionResultChatMessage.class::cast)
                .noneMatch(message -> DocumentToolNames.RETRIEVE_KNOWLEDGE.equals(message.getName()));
    }

    private String initialRetrieveKnowledgeArguments(AgentRunContext context) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "query",
                    initialInstruction(context)
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize initial retrieveKnowledge arguments", exception);
        }
    }

    private String initialInstruction(AgentRunContext context) {
        if (context.getRequest() == null || context.getRequest().getInstruction() == null) {
            return "";
        }
        return context.getRequest().getInstruction();
    }

    private LastRetrieveKnowledgeSnapshot lastRetrieveKnowledgeSnapshot(AgentRunContext context) {
        if (!(context.state().getMemory() instanceof ChatTranscriptMemory transcriptMemory)) {
            return LastRetrieveKnowledgeSnapshot.empty();
        }
        List<ChatMessage> messages = transcriptMemory.getMessages();
        for (int index = messages.size() - 1; index >= 0; index--) {
            ChatMessage message = messages.get(index);
            if (message instanceof ChatMessage.ToolExecutionResultChatMessage toolResultMessage
                    && DocumentToolNames.RETRIEVE_KNOWLEDGE.equals(toolResultMessage.getName())) {
                return new LastRetrieveKnowledgeSnapshot(
                        extractQueries(toolResultMessage.getArgument()),
                        extractChunks(toolResultMessage.getText())
                );
            }
        }
        return LastRetrieveKnowledgeSnapshot.empty();
    }

    private List<String> extractQueries(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return List.of();
        }
        try {
            String query = OBJECT_MAPPER.readTree(arguments).path("query").asText("");
            if (query.isBlank()) {
                return List.of();
            }
            return List.of(query);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<EvidenceChunk> extractChunks(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(text, new TypeReference<List<EvidenceChunk>>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private <T> List<T> defaultList(List<T> values) {
        if (values == null) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private record LastRetrieveKnowledgeSnapshot(List<String> queries, List<EvidenceChunk> chunks) {

        private static LastRetrieveKnowledgeSnapshot empty() {
            return new LastRetrieveKnowledgeSnapshot(List.of(), List.of());
        }
    }
}
