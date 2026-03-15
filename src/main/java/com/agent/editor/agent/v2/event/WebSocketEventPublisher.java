package com.agent.editor.agent.v2.event;

import com.agent.editor.service.TaskQueryService;
import com.agent.editor.websocket.WebSocketService;

/**
 * 事件发布的双写适配器：
 * 一份进入任务查询存储，一份转换成旧前端仍能识别的 WebSocket 消息。
 */
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
        // 查询接口和实时推送共用同一条 ExecutionEvent 流，避免两套状态来源逐渐漂移。
        taskQueryService.appendEvent(event);
        webSocketService.sendToTask(event.taskId(), legacyEventAdapter.toWebSocketMessage(event));
    }

    LegacyEventAdapter legacyAdapter() {
        return legacyEventAdapter;
    }
}
