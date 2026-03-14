package com.agent.editor.agent.v2.tool;

public interface ToolHandler {
    String name();

    ToolResult execute(ToolInvocation invocation, ToolContext context);
}
