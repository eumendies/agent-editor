package com.agent.editor.agent.tool;

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

    /**
     * 注册一个工具处理器；同名工具会被后注册的实现覆盖。
     *
     * @param handler 工具处理器
     */
    public void register(ToolHandler handler) {
        handlers.put(handler.name(), handler);
    }

    /**
     * 按工具名查找处理器。
     *
     * @param name 工具名
     * @return 已注册的处理器；未找到时返回 {@code null}
     */
    public ToolHandler get(String name) {
        return handlers.get(name);
    }

    /**
     * 导出当前注册表中的全部工具规格。
     *
     * @return 全量工具规格列表
     */
    public List<ToolSpecification> specifications() {
        return handlers.values().stream()
                .map(ToolHandler::specification)
                .toList();
    }

    /**
     * 按允许名单导出工具规格；名单为空时退化为全量导出。
     *
     * @param allowedTools 允许暴露给模型的工具名列表
     * @return 过滤后的工具规格列表
     */
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

    /**
     * 判断一个工具名在当前 allowedTools 约束下是否可调用。
     *
     * @param name 工具名
     * @param allowedTools 允许名单；为空时表示不额外限制
     * @return 是否允许调用
     */
    public boolean isAllowed(String name, List<String> allowedTools) {
        // allowedTools 为空时等价于“没有额外限制”，这条规则由 runtime 和 orchestrator 共用。
        return allowedTools == null || allowedTools.isEmpty() || allowedTools.contains(name);
    }
}
