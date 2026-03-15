package com.agent.editor.agent.v2.definition;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.core.agent.ToolCall;
import com.agent.editor.agent.v2.core.runtime.ExecutionContext;
import com.agent.editor.agent.v2.trace.TraceCategory;
import com.agent.editor.agent.v2.trace.TraceCollector;
import com.agent.editor.agent.v2.trace.TraceRecord;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ReactAgentDefinition implements AgentDefinition {

    private final ChatModel chatModel;
    private final TraceCollector traceCollector;

    public ReactAgentDefinition(ChatModel chatModel, TraceCollector traceCollector) {
        this.chatModel = chatModel;
        this.traceCollector = traceCollector;
    }

    @Override
    public AgentType type() {
        return AgentType.REACT;
    }

    @Override
    public Decision decide(ExecutionContext context) {
        if (chatModel == null) {
            return new Decision.Complete("placeholder", "react stub");
        }

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(context);
        traceCollector.collect(traceRecord(
                context,
                TraceCategory.MODEL_REQUEST,
                "react.model.request",
                Map.of(
                        "systemPrompt", systemPrompt,
                        "userPrompt", userPrompt,
                        "toolSpecifications", context.toolSpecifications().stream().map(spec -> spec.name()).toList()
                )
        ));

        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userPrompt)
                ))
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
                "react.model.response",
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
                You are a ReAct-style document editing agent.
                Decide whether to finish directly or call a tool.
                Return concise final text when no tool call is needed.
                """;
    }

    private String buildUserPrompt(ExecutionContext context) {
        String currentContent = context.state().currentContent() != null
                ? context.state().currentContent()
                : context.request().document().content();
        String toolResultBlock = context.state().toolResults().isEmpty()
                ? ""
                : """

                Previous tool results:
                %s

                Use the updated document state and these observations before deciding whether another tool call is necessary.
                """.formatted(renderToolResults(context));
        return """
                Document:
                %s

                Instruction:
                %s
                %s
                """.formatted(
                currentContent,
                context.request().instruction(),
                toolResultBlock
        );
    }

    private String renderToolResults(ExecutionContext context) {
        return context.state().toolResults().stream()
                .map(result -> "- " + result.message())
                .collect(Collectors.joining("\n"));
    }

    private ToolCall toToolCall(ToolExecutionRequest request) {
        return new ToolCall(request.name(), request.arguments());
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
