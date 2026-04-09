package com.agent.editor.agent.mapper;

import com.agent.editor.agent.core.agent.ToolCall;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.core.memory.ExecutionMemory;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;

import java.util.List;

public class ExecutionMemoryChatMessageMapper {

    public List<dev.langchain4j.data.message.ChatMessage> toChatMessages(ExecutionMemory memory) {
        if (!(memory instanceof ChatTranscriptMemory transcriptMemory)) {
            return List.of();
        }

        return transcriptMemory.getMessages().stream()
                .map(this::toChatMessage)
                .toList();
    }

    public dev.langchain4j.data.message.ChatMessage toChatMessage(ChatMessage message) {
        if (message instanceof ChatMessage.SystemChatMessage systemMessage) {
            return SystemMessage.from(systemMessage.getText());
        }
        if (message instanceof ChatMessage.UserChatMessage userMessage) {
            return UserMessage.from(userMessage.getText());
        }
        if (message instanceof ChatMessage.AiChatMessage aiMessage) {
            return AiMessage.from(aiMessage.getText());
        }
        if (message instanceof ChatMessage.AiToolCallChatMessage aiToolCallMessage) {
            return AiMessage.from(
                    aiToolCallMessage.getText(),
                    aiToolCallMessage.getToolCalls().stream()
                            .map(this::toToolExecutionRequest)
                            .toList()
            );
        }
        if (message instanceof ChatMessage.ToolExecutionResultChatMessage toolMessage) {
            return ToolExecutionResultMessage.from(
                    toolMessage.getId(),
                    toolMessage.getName(),
                    toolMessage.getText()
            );
        }
        throw new IllegalStateException("Unsupported execution message type: " + message.getClass().getSimpleName());
    }

    private ToolExecutionRequest toToolExecutionRequest(ToolCall toolCall) {
        return ToolExecutionRequest.builder()
                .id(toolCall.getId())
                .name(toolCall.getName())
                .arguments(toolCall.getArguments())
                .build();
    }
}
