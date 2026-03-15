package com.agent.editor.agent.v2.tool;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * v2 的工具注册表。
 * 它同时承担两件事：runtime 侧按名字查 handler，模型侧按白名单导出可见的 tool specification。
 */
public class ToolRegistry {

    private final Map<String, ToolHandler> handlers = new HashMap<>();

    public void register(ToolHandler handler) {
        handlers.put(handler.name(), handler);
    }

    public ToolHandler get(String name) {
        return handlers.get(name);
    }

    public List<ToolSpecification> specifications() {
        return handlers.values().stream()
                .map(ToolHandler::specification)
                .toList();
    }

    public List<ToolSpecification> specifications(List<String> allowedTools) {
        if (allowedTools == null || allowedTools.isEmpty()) {
            return specifications();
        }

        // 多 agent 场景下，worker 看到的工具集合必须是裁剪后的，而不是全量注册表。
        Set<String> allowed = Set.copyOf(allowedTools);
        return handlers.values().stream()
                .filter(handler -> allowed.contains(handler.name()))
                .map(ToolHandler::specification)
                .toList();
    }

    public boolean isAllowed(String name, List<String> allowedTools) {
        // allowedTools 为空时等价于“没有额外限制”，这条规则由 runtime 和 orchestrator 共用。
        return allowedTools == null || allowedTools.isEmpty() || allowedTools.contains(name);
    }
}
