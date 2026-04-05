package com.agent.editor.agent.v2.tool.memory;

import java.util.ArrayList;
import java.util.List;

/**
 * 主执行链可见的长期记忆工具集合。
 * 这里只给 top-level execution actor 追加 memory 工具，避免 supervisor worker/critic 越权改写长期记忆。
 */
public final class MainAgentMemoryToolAccess {

    private MainAgentMemoryToolAccess() {
    }

    public static List<String> append(List<String> baseTools) {
        List<String> tools = new ArrayList<>(baseTools == null ? List.of() : baseTools);
        addIfAbsent(tools, MemoryToolNames.SEARCH_MEMORY);
        addIfAbsent(tools, MemoryToolNames.UPSERT_MEMORY);
        return List.copyOf(tools);
    }

    private static void addIfAbsent(List<String> tools, String toolName) {
        if (!tools.contains(toolName)) {
            tools.add(toolName);
        }
    }
}
