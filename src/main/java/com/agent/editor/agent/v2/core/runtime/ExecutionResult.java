package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;

import java.util.List;

public record ExecutionResult(String finalMessage, String finalContent, AgentRunContext finalState) {

    public ExecutionResult(String finalMessage) {
        this(finalMessage, finalMessage, new AgentRunContext(
                null,
                0,
                finalMessage,
                new ChatTranscriptMemory(List.of()),
                ExecutionStage.RUNNING,
                null,
                List.of()
        ));
    }

    public ExecutionResult(String finalMessage, String finalContent) {
        this(finalMessage, finalContent, new AgentRunContext(
                null,
                0,
                finalContent,
                new ChatTranscriptMemory(List.of()),
                ExecutionStage.RUNNING,
                null,
                List.of()
        ));
    }
}
