package com.agent.editor.agent.tool.external;

import com.agent.editor.agent.tool.ExecutionToolAccessRole;
import com.agent.editor.agent.tool.document.DocumentToolNames;

import java.util.List;

/**
 * 决定外部能力工具在哪些执行角色下可见。
 */
public class ExternalToolAccessPolicy {

    private static final List<String> WEB_SEARCH_TOOLS = List.of(
            DocumentToolNames.WEB_SEARCH
    );

    public List<String> allowedTools(ExecutionToolAccessRole role) {
        if (role == ExecutionToolAccessRole.MAIN_WRITE || role == ExecutionToolAccessRole.RESEARCH) {
            return WEB_SEARCH_TOOLS;
        }
        return List.of();
    }
}
