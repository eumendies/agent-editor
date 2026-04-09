package com.agent.editor.agent.core.memory;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ChatTranscriptMemory implements ExecutionMemory {

    private List<ChatMessage> messages = List.of();
    private Integer lastObservedTotalTokens;

    public ChatTranscriptMemory(List<ChatMessage> messages) {
        setMessages(messages);
    }

    public ChatTranscriptMemory(List<ChatMessage> messages, Integer lastObservedTotalTokens) {
        setMessages(messages);
        this.lastObservedTotalTokens = lastObservedTotalTokens;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public ChatTranscriptMemory withObservedTotalTokens(Integer totalTokens) {
        return new ChatTranscriptMemory(messages, totalTokens);
    }
}
