package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.agent.ToolCall;
import com.agent.editor.agent.v2.core.runtime.AgentRunContext;
import com.agent.editor.agent.v2.mapper.ExecutionMemoryChatMessageMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.ArrayList;
import java.util.List;

public class GroundedWriterAgentDefinition implements AgentDefinition {

    private final ChatModel chatModel;
    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;

    public GroundedWriterAgentDefinition(ChatModel chatModel) {
        this(chatModel, new ExecutionMemoryChatMessageMapper());
    }

    GroundedWriterAgentDefinition(ChatModel chatModel,
                                  ExecutionMemoryChatMessageMapper memoryChatMessageMapper) {
        this.chatModel = chatModel;
        this.memoryChatMessageMapper = memoryChatMessageMapper;
    }

    @Override
    public AgentType type() {
        return AgentType.REACT;
    }

    @Override
    public Decision decide(AgentRunContext context) {
        if (chatModel == null) {
            return new Decision.Complete("Document updated", "writer stub");
        }

        String systemPrompt = buildSystemPrompt();

        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(buildMessages(context, systemPrompt))
                .toolSpecifications(context.getToolSpecifications())
                .build());

        AiMessage aiMessage = response.aiMessage();
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

    private List<ChatMessage> buildMessages(AgentRunContext context, String systemPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.addAll(memoryChatMessageMapper.toChatMessages(context.state().getMemory()));
        messages.add(UserMessage.from(context.getRequest().getInstruction()));
        return messages;
    }

    private ToolCall toToolCall(ToolExecutionRequest request) {
        return new ToolCall(request.id(), request.name(), request.arguments());
    }
}
