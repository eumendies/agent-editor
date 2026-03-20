package com.agent.editor.agent.v2.core.memory;

import com.agent.editor.agent.v2.core.agent.ToolCall;

import java.util.List;

public sealed interface ChatMessage permits ChatMessage.SystemChatMessage,
        ChatMessage.UserChatMessage,
        ChatMessage.AiChatMessage,
        ChatMessage.AiToolCallChatMessage,
        ChatMessage.ToolExecutionResultChatMessage {

    String text();

    record SystemChatMessage(String text) implements ChatMessage {
    }

    record UserChatMessage(String text) implements ChatMessage {
    }

    record AiChatMessage(String text) implements ChatMessage {
    }

    record AiToolCallChatMessage(String text, List<ToolCall> toolCalls) implements ChatMessage {
    }

    record ToolExecutionResultChatMessage(String id, String name, String argument, String text)
            implements ChatMessage {
    }
}
