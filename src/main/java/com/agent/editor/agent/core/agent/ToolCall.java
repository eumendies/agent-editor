package com.agent.editor.agent.core.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 模型发起的一次工具调用请求，包含调用标识、工具名和参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    // 工具调用唯一 ID，用于串联执行结果。
    private String id;
    // 被调用的工具名称。
    private String name;
    // 工具调用参数，通常是 JSON 字符串。
    private String arguments;

    public ToolCall(String name, String arguments) {
        this(UUID.randomUUID().toString(), name, arguments);
    }
}
