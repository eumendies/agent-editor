package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.agent.ToolCall;
import com.agent.editor.agent.v2.core.runtime.ExecutionContext;
import com.agent.editor.agent.v2.mapper.ExecutionMemoryChatMessageMapper;
import com.agent.editor.agent.v2.trace.TraceCategory;
import com.agent.editor.agent.v2.trace.TraceCollector;
import com.agent.editor.agent.v2.trace.TraceRecord;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GroundedWriterAgentDefinition implements AgentDefinition {

    private final ChatModel chatModel;
    private final TraceCollector traceCollector;
    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;

    public GroundedWriterAgentDefinition(ChatModel chatModel, TraceCollector traceCollector) {
        this(chatModel, traceCollector, new ExecutionMemoryChatMessageMapper());
    }

    GroundedWriterAgentDefinition(ChatModel chatModel,
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
    public Decision decide(ExecutionContext context) {
        if (chatModel == null) {
            return new Decision.Complete("Document updated", "writer stub");
        }

        String systemPrompt = buildSystemPrompt();
        traceCollector.collect(traceRecord(
                context,
                TraceCategory.MODEL_REQUEST,
                "writer.model.request",
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
                "writer.model.response",
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
                You are a grounded writer worker in a hybrid supervisor workflow.
                Write or revise the document using only the available context and retrieved evidence in memory.
                Do not introduce claims that are not supported by the evidence already present in the conversation.
                Use editDocument when you need to update the document content.
                If searchContent is available, use it only to inspect the current draft before editing.
                Keep your final text concise once the document update is complete.
                """;
    }

    private List<ChatMessage> buildMessages(ExecutionContext context, String systemPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.addAll(memoryChatMessageMapper.toChatMessages(context.state().memory()));
        messages.add(UserMessage.from(context.request().instruction()));
        return messages;
    }

    private ToolCall toToolCall(ToolExecutionRequest request) {
        return new ToolCall(request.id(), request.name(), request.arguments());
    }

    private TraceRecord traceRecord(ExecutionContext context,
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
