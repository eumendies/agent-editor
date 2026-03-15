package com.agent.editor.agent.v2.event;

import com.agent.editor.dto.WebSocketMessage;
import com.agent.editor.model.AgentStep;
import com.agent.editor.model.AgentStepType;

import java.util.UUID;

/**
 * 把 v2 的标准事件流压缩回旧前端还能消费的 step / websocket 结构。
 * 这是兼容层，不是新的领域模型本体。
 */
public class LegacyEventAdapter {

    public AgentStep toStep(ExecutionEvent event, int stepNumber) {
        AgentStepType stepType = toStepType(event.type());
        AgentStep step = new AgentStep(UUID.randomUUID().toString(), event.taskId(), stepNumber, stepType);

        // 旧前端是“一个大 step 对象 + 若干展示字段”，这里按事件类型把 message 投影到不同字段。
        switch (stepType) {
            case ACTION -> step.setAction(event.message());
            case OBSERVATION -> step.setObservation(event.message());
            case RESULT, COMPLETED -> {
                step.setResult(event.message());
                step.setFinal(stepType == AgentStepType.COMPLETED);
            }
            case ERROR -> step.setError(event.message());
            default -> step.setThought(event.message());
        }

        return step;
    }

    public WebSocketMessage toWebSocketMessage(ExecutionEvent event) {
        return switch (event.type()) {
            case TASK_COMPLETED -> WebSocketMessage.completed(event.taskId(), event.message());
            case SUPERVISOR_COMPLETED -> WebSocketMessage.completed(event.taskId(), event.message());
            case TASK_FAILED, TOOL_FAILED -> WebSocketMessage.error(event.taskId(), event.message());
            // 其余中间态一律按 step 事件推送，保持旧页面的消费方式不变。
            default -> WebSocketMessage.step(event.taskId(), toStepType(event.type()), event.message());
        };
    }

    private AgentStepType toStepType(EventType eventType) {
        return switch (eventType) {
            case TOOL_CALLED, WORKER_SELECTED -> AgentStepType.ACTION;
            case TOOL_SUCCEEDED, WORKER_COMPLETED -> AgentStepType.OBSERVATION;
            case TASK_COMPLETED, SUPERVISOR_COMPLETED -> AgentStepType.COMPLETED;
            case TASK_FAILED, TOOL_FAILED -> AgentStepType.ERROR;
            default -> AgentStepType.THINKING;
        };
    }
}
