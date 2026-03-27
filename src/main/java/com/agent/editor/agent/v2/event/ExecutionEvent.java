package com.agent.editor.agent.v2.event;

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
