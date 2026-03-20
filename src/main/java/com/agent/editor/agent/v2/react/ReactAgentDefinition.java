package com.agent.editor.agent.v2.react;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.agent.AgentDefinition;
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

public class ReactAgentDefinition implements AgentDefinition {

    private final ChatModel chatModel;
    private final TraceCollector traceCollector;
    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;

    public ReactAgentDefinition(ChatModel chatModel, TraceCollector traceCollector) {
        this(chatModel, traceCollector, new ExecutionMemoryChatMessageMapper());
    }

    ReactAgentDefinition(ChatModel chatModel,
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
            return new Decision.Complete("placeholder", "react stub");
        }

        // ReAct 在 v2 里仍然是“单轮决策器”，真正的循环由 runtime 负责。
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(context);
        traceCollector.collect(traceRecord(
                context,
                TraceCategory.MODEL_REQUEST,
                "react.model.request",
                Map.of(
                        "systemPrompt", systemPrompt,
                        "userPrompt", userPrompt,
                        "memoryMessages", context.state().memory(),
                        "toolSpecifications", context.toolSpecifications().stream().map(spec -> spec.name()).toList()
                )
        ));

        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(buildMessages(context, systemPrompt, userPrompt))
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
            // 这里不直接执行工具，只把调用意图翻译成统一 Decision，由 runtime 接管后续步骤。
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
        // prompt 总是优先使用当前执行态里的文档内容，而不是请求初始快照。
        String currentContent = context.state().currentContent() != null
                ? context.state().currentContent()
                : context.request().document().content();
        return """
                Document:
                %s

                Instruction:
                %s
                """.formatted(
                currentContent,
                context.request().instruction()
        );
    }

    private List<ChatMessage> buildMessages(ExecutionContext context, String systemPrompt, String userPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.addAll(memoryChatMessageMapper.toChatMessages(context.state().memory()));
        // messages.add(UserMessage.from(userPrompt));
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
