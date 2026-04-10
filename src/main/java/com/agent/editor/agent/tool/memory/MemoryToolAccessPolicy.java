package com.agent.editor.agent.tool.memory;

import com.agent.editor.agent.tool.ExecutionToolAccessRole;

import java.util.List;

/**
 * 决定长期记忆工具在哪些执行角色下可见。
 */
public class MemoryToolAccessPolicy {

    private static final List<String> READ_WRITE_TOOLS = List.of(
            MemoryToolNames.SEARCH_MEMORY,
            MemoryToolNames.UPSERT_MEMORY
    );
    private static final List<String> REVIEW_TOOLS = List.of(
            MemoryToolNames.SEARCH_MEMORY
    );

    /**
     * 根据执行角色返回允许暴露的长期记忆工具。
     *
     * @param role 执行阶段角色
     * @return 长期记忆工具白名单
     */
    public List<String> allowedTools(ExecutionToolAccessRole role) {
        if (role == ExecutionToolAccessRole.MAIN_WRITE || role == ExecutionToolAccessRole.MEMORY) {
            return READ_WRITE_TOOLS;
        }
        if (role == ExecutionToolAccessRole.REVIEW) {
            return REVIEW_TOOLS;
        }
        return List.of();
    }
}
