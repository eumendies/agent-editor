package com.agent.editor.agent.v2.core.agent;

import com.agent.editor.agent.v2.core.context.AgentRunContext;

public interface ToolLoopAgent extends Agent {
    /**
     * 基于当前执行上下文做一次决策。
     * 返回值只表达“结束 / 回复 / 调工具”，不直接暴露底层模型对象。
     */
    ToolLoopDecision decide(AgentRunContext context);
}
