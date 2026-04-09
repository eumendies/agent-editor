package com.agent.editor.agent.core.state;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务在调度层暴露的最小状态视图，用于前端追踪执行结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskState {

    // 任务唯一标识。
    private String taskId;
    // 当前任务状态。
    private TaskStatus status;
    // 任务完成后产出的最终正文。
    private String finalContent;
}
