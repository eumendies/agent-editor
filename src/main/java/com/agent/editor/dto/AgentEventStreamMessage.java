package com.agent.editor.dto;

import com.agent.editor.agent.event.ExecutionEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentEventStreamMessage {

    private String type;
    private ExecutionEvent event;
    private String sessionId;

    public static AgentEventStreamMessage connected(String sessionId) {
        return new AgentEventStreamMessage("CONNECTED", null, sessionId);
    }

    public static AgentEventStreamMessage event(ExecutionEvent event) {
        return new AgentEventStreamMessage("EVENT", event, null);
    }
}
