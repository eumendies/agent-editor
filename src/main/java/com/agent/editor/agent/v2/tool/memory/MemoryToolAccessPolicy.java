package com.agent.editor.agent.v2.tool.memory;

import com.agent.editor.agent.v2.tool.ExecutionToolAccessRole;

import java.util.List;

/**
 * 决定长期记忆工具在哪些执行角色下可见。
 */
public class MemoryToolAccessPolicy {

    private static final List<String> MAIN_WRITE_TOOLS = List.of(
            MemoryToolNames.SEARCH_MEMORY
    );
    private static final List<String> MEMORY_WORKER_TOOLS = List.of(
            MemoryToolNames.SEARCH_MEMORY,
            MemoryToolNames.UPSERT_MEMORY
    );

    /**
     * 根据执行角色返回允许暴露的长期记忆工具。
     *
     * @param role 执行阶段角色
     * @return 长期记忆工具白名单
     */
    public List<String> allowedTools(ExecutionToolAccessRole role) {
        if (role == ExecutionToolAccessRole.MAIN_WRITE) {
            return MAIN_WRITE_TOOLS;
        }
        if (role == ExecutionToolAccessRole.MEMORY) {
            return MEMORY_WORKER_TOOLS;
        }
        return List.of();
    }
}
