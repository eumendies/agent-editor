package com.agent.editor.agent.event;

import com.agent.editor.service.TaskQueryService;
import com.agent.editor.websocket.WebSocketService;

/**
 * 事件发布器：
 * 一份进入任务查询存储，一份进入原生 websocket 推送。
 */
public class WebSocketEventPublisher implements EventPublisher {

    private final TaskQueryService taskQueryService;
    private final WebSocketService webSocketService;

    public WebSocketEventPublisher(TaskQueryService taskQueryService,
                                   WebSocketService webSocketService) {
        this.taskQueryService = taskQueryService;
        this.webSocketService = webSocketService;
    }

    @Override
    public void publish(ExecutionEvent event) {
        // 查询接口和实时推送共用同一条 ExecutionEvent 流，避免两套状态来源逐渐漂移。
        taskQueryService.appendEvent(event);
        webSocketService.sendEventToTask(event.getTaskId(), event);
    }
}
