package com.agent.editor.agent.v2.event;

import com.agent.editor.service.TaskQueryService;
import com.agent.editor.websocket.WebSocketService;

public class WebSocketEventPublisher implements EventPublisher {

    private final TaskQueryService taskQueryService;
    private final WebSocketService webSocketService;
    private final LegacyEventAdapter legacyEventAdapter;

    public WebSocketEventPublisher(TaskQueryService taskQueryService,
                                   WebSocketService webSocketService,
                                   LegacyEventAdapter legacyEventAdapter) {
        this.taskQueryService = taskQueryService;
        this.webSocketService = webSocketService;
        this.legacyEventAdapter = legacyEventAdapter;
    }

    @Override
    public void publish(ExecutionEvent event) {
        taskQueryService.appendEvent(event);
        webSocketService.sendToTask(event.taskId(), legacyEventAdapter.toWebSocketMessage(event));
    }

    LegacyEventAdapter legacyAdapter() {
        return legacyEventAdapter;
    }
}
