package com.agent.editor.agent.v2.core.memory;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ChatTranscriptMemory implements ExecutionMemory {

    private List<ChatMessage> messages = List.of();

    public ChatTranscriptMemory(List<ChatMessage> messages) {
        setMessages(messages);
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
