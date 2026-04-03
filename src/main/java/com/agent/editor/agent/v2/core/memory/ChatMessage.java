package com.agent.editor.agent.v2.core.memory;

import com.agent.editor.agent.v2.core.agent.ToolCall;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 执行记忆中保存的对话消息抽象，覆盖系统、用户、AI 与工具交互消息。
 */
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

        // 系统消息正文。
        private String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class UserChatMessage implements ChatMessage {

        // 用户消息正文。
        private String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class AiChatMessage implements ChatMessage {

        // AI 普通回复正文。
        private String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class AiToolCallChatMessage implements ChatMessage {

        // 触发工具调用前的模型回复文本。
        private String text;
        // 模型请求执行的工具调用列表。
        private List<ToolCall> toolCalls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class ToolExecutionResultChatMessage implements ChatMessage {

        // 对应工具调用的唯一 ID。
        private String id;
        // 工具名称。
        private String name;
        // 调用时的参数文本。
        private String argument;
        // 工具执行返回的文本结果。
        private String text;
    }
}
