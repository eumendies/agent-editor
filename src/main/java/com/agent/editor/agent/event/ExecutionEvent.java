package com.agent.editor.agent.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionEvent {

    private EventType type;
    private String taskId;
    private String message;
}
