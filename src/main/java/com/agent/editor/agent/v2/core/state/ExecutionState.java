package com.agent.editor.agent.v2.core.state;

import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.ExecutionMemory;
import com.agent.editor.agent.v2.tool.ToolResult;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
                .map(result -> new ChatMessage.ToolExecutionResultChatMessage(
                        UUID.randomUUID().toString(),
                        "tool",
                        null,
                        result.message()
                ))
                .map(ChatMessage.class::cast)
                .toList()), completed ? ExecutionStage.COMPLETED : ExecutionStage.RUNNING, null);
    }

    public ExecutionState {
        Objects.requireNonNull(memory, "memory must not be null");
        Objects.requireNonNull(stage, "stage must not be null");
    }

    public ExecutionState withStage(ExecutionStage nextStage) {
        return new ExecutionState(iteration, currentContent, memory, nextStage, pendingReason);
    }

    public ExecutionState withCurrentContent(String nextContent) {
        return new ExecutionState(iteration, nextContent, memory, stage, pendingReason);
    }

    public ExecutionState advance(String nextContent) {
        return new ExecutionState(iteration + 1, nextContent, memory, ExecutionStage.RUNNING, pendingReason);
    }

    public ExecutionState advance(String nextContent, ExecutionMemory nextMemory) {
        return new ExecutionState(iteration + 1, nextContent, nextMemory, ExecutionStage.RUNNING, pendingReason);
    }

    public ExecutionState markCompleted() {
        return new ExecutionState(iteration, currentContent, memory, ExecutionStage.COMPLETED, pendingReason);
    }

    public ExecutionState appendMemory(ChatMessage message) {
        return appendMemory(List.of(message));
    }

    public ExecutionState appendMemory(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return this;
        }
        if (!(memory instanceof ChatTranscriptMemory transcriptMemory)) {
            throw new IllegalStateException("Execution memory does not support transcript append: "
                    + memory.getClass().getSimpleName());
        }
        List<ChatMessage> merged = new java.util.ArrayList<>(transcriptMemory.messages());
        merged.addAll(messages);
        return new ExecutionState(iteration, currentContent, new ChatTranscriptMemory(merged), stage, pendingReason);
    }

    public boolean completed() {
        return stage == ExecutionStage.COMPLETED;
    }

    public List<ToolResult> toolResults() {
        if (!(memory instanceof ChatTranscriptMemory transcriptMemory)) {
            return List.of();
        }
        return transcriptMemory.messages().stream()
                .filter(ChatMessage.ToolExecutionResultChatMessage.class::isInstance)
                .map(ChatMessage.ToolExecutionResultChatMessage.class::cast)
                .map(message -> new ToolResult(message.text()))
                .toList();
    }
}
