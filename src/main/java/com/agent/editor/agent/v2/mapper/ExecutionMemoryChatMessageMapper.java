package com.agent.editor.agent.v2.mapper;

import com.agent.editor.agent.v2.core.state.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.ExecutionMemory;
import com.agent.editor.agent.v2.core.state.ExecutionMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.List;

public class ExecutionMemoryChatMessageMapper {

    public List<ChatMessage> toChatMessages(ExecutionMemory memory) {
        if (!(memory instanceof ChatTranscriptMemory transcriptMemory)) {
            return List.of();
        }

        return transcriptMemory.messages().stream()
                .map(this::toChatMessage)
                .toList();
    }

    public ChatMessage toChatMessage(ExecutionMessage message) {
        if (message instanceof ExecutionMessage.SystemExecutionMessage systemMessage) {
            return SystemMessage.from(systemMessage.text());
        }
        if (message instanceof ExecutionMessage.UserExecutionMessage userMessage) {
            return UserMessage.from(userMessage.text());
        }
        if (message instanceof ExecutionMessage.AiExecutionMessage aiMessage) {
            return AiMessage.from(aiMessage.text());
        }
        if (message instanceof ExecutionMessage.ToolExecutionResultExecutionMessage toolMessage) {
            return UserMessage.from(toolMessage.text());
        }
        throw new IllegalStateException("Unsupported execution message type: " + message.getClass().getSimpleName());
    }
}
