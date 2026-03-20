package com.agent.editor.agent.v2.task;

import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.ExecutionMemory;
import com.agent.editor.agent.v2.core.state.TaskStatus;

import java.util.List;

public record TaskResult(TaskStatus status, String finalContent, ExecutionMemory memory) {

    public TaskResult(TaskStatus status, String finalContent) {
        this(status, finalContent, new ChatTranscriptMemory(List.of()));
    }

    public TaskResult withMemory(ExecutionMemory memory) {
        return new TaskResult(status, finalContent, memory);
    }
}
