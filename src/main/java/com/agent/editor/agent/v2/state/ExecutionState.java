package com.agent.editor.agent.v2.state;

import com.agent.editor.agent.v2.tool.ToolResult;

import java.util.List;

public record ExecutionState(int iteration, boolean completed, String currentContent, List<ToolResult> toolResults) {

    public ExecutionState(int iteration, boolean completed) {
        this(iteration, completed, null, List.of());
    }

    public ExecutionState(int iteration, boolean completed, String currentContent) {
        this(iteration, completed, currentContent, List.of());
    }

    public ExecutionState {
        toolResults = List.copyOf(toolResults);
    }
}
