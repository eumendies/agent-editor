package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.ToolCall;
import com.agent.editor.agent.v2.tool.ToolResult;

import java.util.List;

/**
 * 允许 agent 在 runtime 完成工具执行后，把工具交互历史同步到会话 memory。
 */
public interface ToolExecutionMemoryBridge {

    void rememberToolExecution(ExecutionRequest request, List<ToolCall> calls, List<ToolResult> results);
}
