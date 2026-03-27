package com.agent.editor.agent.v2.task;

import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.ExecutionMemory;
import com.agent.editor.agent.v2.core.state.TaskStatus;
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
