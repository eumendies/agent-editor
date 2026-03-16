package com.agent.editor.agent.v2.core.state;

import java.util.List;

public record ChatTranscriptMemory(List<ExecutionMessage> messages) implements ExecutionMemory {

    public ChatTranscriptMemory {
        messages = List.copyOf(messages);
    }
}
