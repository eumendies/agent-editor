package com.agent.editor.agent.v2.core.state;

import com.agent.editor.agent.v2.tool.ToolResult;

import java.util.List;
import java.util.Objects;

public record ExecutionState(
        int iteration,
        String currentContent,
        ExecutionMemory memory,
        ExecutionStage stage,
        String pendingReason
) {

    public ExecutionState(int iteration, String currentContent) {
        this(iteration, currentContent, new ChatTranscriptMemory(List.of()), ExecutionStage.RUNNING, null);
    }

    public ExecutionState(int iteration, boolean completed) {
        this(iteration, null, new ChatTranscriptMemory(List.of()),
                completed ? ExecutionStage.COMPLETED : ExecutionStage.RUNNING, null);
    }

    public ExecutionState(int iteration, boolean completed, String currentContent) {
        this(iteration, currentContent, new ChatTranscriptMemory(List.of()),
                completed ? ExecutionStage.COMPLETED : ExecutionStage.RUNNING, null);
    }

    public ExecutionState(int iteration, boolean completed, String currentContent, List<ToolResult> toolResults) {
        this(iteration, currentContent, new ChatTranscriptMemory(toolResults.stream()
                .map(result -> new ExecutionMessage.ToolExecutionResultExecutionMessage(result.message()))
                .map(ExecutionMessage.class::cast)
                .toList()), completed ? ExecutionStage.COMPLETED : ExecutionStage.RUNNING, null);
    }

    public ExecutionState {
        Objects.requireNonNull(memory, "memory must not be null");
        Objects.requireNonNull(stage, "stage must not be null");
    }

    public boolean completed() {
        return stage == ExecutionStage.COMPLETED;
    }

    public List<ToolResult> toolResults() {
        if (!(memory instanceof ChatTranscriptMemory transcriptMemory)) {
            return List.of();
        }
        return transcriptMemory.messages().stream()
                .filter(ExecutionMessage.ToolExecutionResultExecutionMessage.class::isInstance)
                .map(ExecutionMessage.ToolExecutionResultExecutionMessage.class::cast)
                .map(message -> new ToolResult(message.text()))
                .toList();
    }
}
