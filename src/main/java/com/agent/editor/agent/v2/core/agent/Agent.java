package com.agent.editor.agent.v2.core.agent;

import com.agent.editor.agent.v2.core.runtime.AgentRunContext;

/**
 * 统一的 agent 决策接口。
 * runtime 只依赖这个抽象，不关心具体实现是 ReAct、planner 还是 supervisor。
 */
public interface Agent {
    AgentType type();

    /**
     * 基于当前执行上下文做一次决策。
     * 返回值只表达“结束 / 回复 / 调工具”，不直接暴露底层模型对象。
     */
    Decision decide(AgentRunContext context);
}
