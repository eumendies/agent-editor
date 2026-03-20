package com.agent.editor.agent.v2.core.memory;

import java.util.List;

public record ChatTranscriptMemory(List<ChatMessage> messages) implements ExecutionMemory {

    public ChatTranscriptMemory {
        messages = List.copyOf(messages);
    }
}
