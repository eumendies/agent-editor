package com.agent.editor.agent.v2.mapper;

import com.agent.editor.agent.v2.core.agent.ToolCall;
import com.agent.editor.agent.v2.core.state.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.ExecutionMemory;
import com.agent.editor.agent.v2.core.state.ChatMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;

import java.util.List;

public class ExecutionMemoryChatMessageMapper {

    public List<dev.langchain4j.data.message.ChatMessage> toChatMessages(ExecutionMemory memory) {
        if (!(memory instanceof ChatTranscriptMemory transcriptMemory)) {
            return List.of();
        }

        return transcriptMemory.messages().stream()
                .map(this::toChatMessage)
                .toList();
    }

    public dev.langchain4j.data.message.ChatMessage toChatMessage(ChatMessage message) {
        if (message instanceof ChatMessage.SystemChatMessage systemMessage) {
            return SystemMessage.from(systemMessage.text());
        }
        if (message instanceof ChatMessage.UserChatMessage userMessage) {
            return UserMessage.from(userMessage.text());
        }
        if (message instanceof ChatMessage.AiChatMessage aiMessage) {
            return AiMessage.from(aiMessage.text());
        }
        if (message instanceof ChatMessage.AiToolCallChatMessage aiToolCallMessage) {
            return AiMessage.from(
                    aiToolCallMessage.text(),
                    aiToolCallMessage.toolCalls().stream()
                            .map(this::toToolExecutionRequest)
                            .toList()
            );
        }
        if (message instanceof ChatMessage.ToolExecutionResultChatMessage toolMessage) {
            return ToolExecutionResultMessage.from(toolMessage.id(), toolMessage.name(), toolMessage.text());
        }
        throw new IllegalStateException("Unsupported execution message type: " + message.getClass().getSimpleName());
    }

    private ToolExecutionRequest toToolExecutionRequest(ToolCall toolCall) {
        return ToolExecutionRequest.builder()
                .id(toolCall.id())
                .name(toolCall.name())
                .arguments(toolCall.arguments())
                .build();
    }
}
