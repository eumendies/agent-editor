package com.agent.editor.agent.task;

import com.agent.editor.agent.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.core.memory.ExecutionMemory;
import com.agent.editor.agent.core.state.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskResult {

    private TaskStatus status;
    private String finalContent;
    private ExecutionMemory memory;

    public TaskResult(TaskStatus status, String finalContent) {
        this(status, finalContent, new ChatTranscriptMemory(List.of()));
    }

    public TaskResult withMemory(ExecutionMemory memory) {
        return new TaskResult(status, finalContent, memory);
    }
}
