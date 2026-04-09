package com.agent.editor.agent.event;

import com.agent.editor.dto.WebSocketMessage;
import com.agent.editor.model.AgentStepType;
import com.agent.editor.service.TaskQueryService;
import com.agent.editor.websocket.WebSocketService;

/**
 * 事件发布的双写适配器：
 * 一份进入任务查询存储，一份进入原生 v2 websocket，
 * 同时保留旧 websocket 所需的最小兼容投影。
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
        webSocketService.sendEventToV2Task(event.getTaskId(), event);
        // v1 websocket 仍然保留给兼容客户端，所以这里只保留最小旧协议投影，不再单独维护适配类。
        webSocketService.sendToTask(event.getTaskId(), toLegacyWebSocketMessage(event));
    }

    private WebSocketMessage toLegacyWebSocketMessage(ExecutionEvent event) {
        return switch (event.getType()) {
            case TASK_COMPLETED, SUPERVISOR_COMPLETED -> WebSocketMessage.completed(event.getTaskId(), event.getMessage());
            case TASK_FAILED, TOOL_FAILED -> WebSocketMessage.error(event.getTaskId(), event.getMessage());
            default -> WebSocketMessage.step(event.getTaskId(), toLegacyStepType(event.getType()), event.getMessage());
        };
    }

    private AgentStepType toLegacyStepType(EventType eventType) {
        return switch (eventType) {
            case TOOL_CALLED, WORKER_SELECTED -> AgentStepType.ACTION;
            case TOOL_SUCCEEDED, WORKER_COMPLETED -> AgentStepType.OBSERVATION;
            case TASK_COMPLETED, SUPERVISOR_COMPLETED -> AgentStepType.COMPLETED;
            case TASK_FAILED, TOOL_FAILED -> AgentStepType.ERROR;
            default -> AgentStepType.THINKING;
        };
    }
}
