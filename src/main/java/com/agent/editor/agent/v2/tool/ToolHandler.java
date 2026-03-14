package com.agent.editor.agent.v2.tool;

import dev.langchain4j.agent.tool.ToolSpecification;

public interface ToolHandler {
    String name();

    ToolSpecification specification();

    ToolResult execute(ToolInvocation invocation, ToolContext context);
}
