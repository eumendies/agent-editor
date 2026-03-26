package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.agent.ToolCall;
import com.agent.editor.agent.v2.core.runtime.AgentRunContext;
import com.agent.editor.agent.v2.mapper.ExecutionMemoryChatMessageMapper;
import com.agent.editor.agent.v2.trace.TraceCategory;
import com.agent.editor.agent.v2.trace.TraceCollector;
import com.agent.editor.agent.v2.trace.TraceRecord;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EvidenceReviewerAgentDefinition implements AgentDefinition {

    private final ChatModel chatModel;
    private final TraceCollector traceCollector;
    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;

    public EvidenceReviewerAgentDefinition(ChatModel chatModel, TraceCollector traceCollector) {
        this(chatModel, traceCollector, new ExecutionMemoryChatMessageMapper());
    }

    EvidenceReviewerAgentDefinition(ChatModel chatModel,
                                    TraceCollector traceCollector,
                                    ExecutionMemoryChatMessageMapper memoryChatMessageMapper) {
        this.chatModel = chatModel;
        this.traceCollector = traceCollector;
        this.memoryChatMessageMapper = memoryChatMessageMapper;
    }

    @Override
    public AgentType type() {
        return AgentType.REACT;
    }

    @Override
    public Decision decide(AgentRunContext context) {
        if (chatModel == null) {
            return new Decision.Complete("{}", "reviewer stub");
        }

        String systemPrompt = buildSystemPrompt();
        traceCollector.collect(traceRecord(
                context,
                TraceCategory.MODEL_REQUEST,
                "reviewer.model.request",
                Map.of(
                        "systemPrompt", systemPrompt,
                        "memoryMessages", context.state().memory(),
                        "toolSpecifications", context.toolSpecifications().stream().map(spec -> spec.name()).toList()
                )
        ));

        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(buildMessages(context, systemPrompt))
                .toolSpecifications(context.toolSpecifications())
                .build());

        AiMessage aiMessage = response.aiMessage();
        Map<String, Object> responsePayload = new LinkedHashMap<>();
        responsePayload.put("rawText", aiMessage.text());
        responsePayload.put("toolCalls", aiMessage.toolExecutionRequests().stream()
                .map(request -> Map.of(
                        "name", request.name(),
                        "arguments", request.arguments()
                ))
                .toList());
        traceCollector.collect(traceRecord(
                context,
                TraceCategory.MODEL_RESPONSE,
                "reviewer.model.response",
                responsePayload
        ));

        if (aiMessage.hasToolExecutionRequests()) {
            return new Decision.ToolCalls(
                    aiMessage.toolExecutionRequests().stream()
                            .map(this::toToolCall)
                            .toList(),
                    aiMessage.text()
            );
        }

        return new Decision.Complete(aiMessage.text(), aiMessage.text());
    }

    private String buildSystemPrompt() {
        return """
                You are a reviewer worker in a hybrid supervisor workflow.
                Review whether the latest answer follows the user instruction and stays grounded in the available evidence.
                If you need more local inspection, use the available analysis tools before finalizing your review.
                Finish by returning strict JSON matching the ReviewerFeedback shape.
                ReviewerFeedback must explicitly report verdict, instructionSatisfied, evidenceGrounded,
                unsupportedClaims, missingRequirements, feedback, and reasoning.
                """;
    }

    private List<ChatMessage> buildMessages(AgentRunContext context, String systemPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.addAll(memoryChatMessageMapper.toChatMessages(context.state().memory()));
        return messages;
    }

    private ToolCall toToolCall(ToolExecutionRequest request) {
        return new ToolCall(request.id(), request.name(), request.arguments());
    }

    private TraceRecord traceRecord(AgentRunContext context,
                                    TraceCategory category,
                                    String stage,
                                    Map<String, Object> payload) {
        return new TraceRecord(
                UUID.randomUUID().toString(),
                context.request().taskId(),
                Instant.now(),
                category,
                stage,
                type(),
                context.request().workerId(),
                context.state().iteration(),
                payload
        );
    }
}
