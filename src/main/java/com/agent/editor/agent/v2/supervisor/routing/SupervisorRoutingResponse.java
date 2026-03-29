package com.agent.editor.agent.v2.supervisor.routing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型返回的 supervisor 路由结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
class SupervisorRoutingResponse {

    // 与 AI service 约定的结构化路由结果，字段保持扁平，方便模型稳定输出 JSON。
    private SupervisorAction action;
    private String workerId;
    private String instruction;
    private String summary;
    private String finalContent;
    private String reasoning;
}
