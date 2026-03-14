package com.agent.editor.agent.v2.state;

import com.agent.editor.agent.v2.tool.ToolResult;

import java.util.List;

public record ExecutionState(int iteration, boolean completed, List<ToolResult> toolResults) {

    public ExecutionState(int iteration, boolean completed) {
        this(iteration, completed, List.of());
    }

    public ExecutionState {
        toolResults = List.copyOf(toolResults);
    }
}
