package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.*;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.context.ModelInvocationContext;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.util.StructuredOutputParsers;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;
import java.util.Map;

/**
 * research worker。
 * 只负责拉取与整理证据，不直接改写文档正文。
 */
public class ResearcherAgent implements ToolLoopAgent {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatModel chatModel;
    private final ResearcherAgentContextFactory contextFactory;

    public ResearcherAgent(ChatModel chatModel) {
        this(chatModel, new ResearcherAgentContextFactory());
    }

    public ResearcherAgent(ChatModel chatModel,
                           ResearcherAgentContextFactory contextFactory) {
        this.chatModel = chatModel;
        this.contextFactory = contextFactory;
    }

    @Override
    public AgentType type() {
        return AgentType.REACT;
    }

    @Override
    public ToolLoopDecision decide(AgentRunContext context) {
        if (chatModel == null) {
            return new ToolLoopDecision.Complete("{}", "researcher stub");
        }
        // 首轮先用用户原始 instruction 做一次固定召回，避免模型在没有证据前过早改写查询导致召回偏移。
        if (shouldRunInitialInstructionRetrieval(context)) {
            return new ToolLoopDecision.ToolCalls(
                    List.of(new ToolCall("retrieveKnowledge", initialRetrieveKnowledgeArguments(context))),
                    "initial instruction retrieval"
            );
        }

        ModelInvocationContext invocationContext = contextFactory.buildModelInvocationContext(context);
        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(invocationContext.getMessages())
                .toolSpecifications(invocationContext.getToolSpecifications())
                .build());

        AiMessage aiMessage = response.aiMessage();
        if (aiMessage.hasToolExecutionRequests()) {
            return new ToolLoopDecision.ToolCalls(
                    aiMessage.toolExecutionRequests().stream()
                            .map(this::toToolCall)
                            .toList(),
                    aiMessage.text()
            );
        }

        EvidencePackage evidencePackage = parseEvidencePackage(aiMessage.text());
        if (evidencePackage != null) {
            return new ToolLoopDecision.Complete<>(evidencePackage, aiMessage.text());
        }
        return new ToolLoopDecision.Complete<>(aiMessage.text(), aiMessage.text());
    }

    private ToolCall toToolCall(ToolExecutionRequest request) {
        return new ToolCall(request.id(), request.name(), request.arguments());
    }

    private EvidencePackage parseEvidencePackage(String text) {
        return StructuredOutputParsers.parseJsonWithMarkdownCleanup(text, EvidencePackage.class);
    }

    private boolean shouldRunInitialInstructionRetrieval(AgentRunContext context) {
        if (!(context.state().getMemory() instanceof ChatTranscriptMemory transcriptMemory)) {
            return false;
        }
        return transcriptMemory.getMessages().stream()
                .filter(ChatMessage.ToolExecutionResultChatMessage.class::isInstance)
                .map(ChatMessage.ToolExecutionResultChatMessage.class::cast)
                .noneMatch(message -> "retrieveKnowledge".equals(message.getName()));
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
}
