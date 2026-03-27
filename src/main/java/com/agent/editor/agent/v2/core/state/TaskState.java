package com.agent.editor.agent.v2.core.state;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskState {

    private String taskId;
    private TaskStatus status;
    private String finalContent;
}
