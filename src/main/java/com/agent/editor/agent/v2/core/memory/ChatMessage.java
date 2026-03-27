package com.agent.editor.agent.v2.core.memory;

import com.agent.editor.agent.v2.core.agent.ToolCall;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public sealed interface ChatMessage permits ChatMessage.SystemChatMessage,
        ChatMessage.UserChatMessage,
        ChatMessage.AiChatMessage,
        ChatMessage.AiToolCallChatMessage,
        ChatMessage.ToolExecutionResultChatMessage {

    String getText();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class SystemChatMessage implements ChatMessage {

        private String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class UserChatMessage implements ChatMessage {

        private String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class AiChatMessage implements ChatMessage {

        private String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class AiToolCallChatMessage implements ChatMessage {

        private String text;
        private List<ToolCall> toolCalls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class ToolExecutionResultChatMessage implements ChatMessage {

        private String id;
        private String name;
        private String argument;
        private String text;
    }
}
