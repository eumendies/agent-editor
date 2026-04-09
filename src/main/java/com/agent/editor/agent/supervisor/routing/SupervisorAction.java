package com.agent.editor.agent.supervisor.routing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * supervisor 路由阶段允许的动作枚举。
 */
enum SupervisorAction {
    ASSIGN_WORKER("assign_worker"),
    COMPLETE("complete");

    private final String wireValue;

    SupervisorAction(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static SupervisorAction fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        // 同时兼容枚举名和线上的 wire value，降低模型大小写或输出格式波动带来的解析失败。
        for (SupervisorAction action : values()) {
            if (action.name().equalsIgnoreCase(value) || action.wireValue.equalsIgnoreCase(value)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unsupported supervisor action: " + value);
    }
}
