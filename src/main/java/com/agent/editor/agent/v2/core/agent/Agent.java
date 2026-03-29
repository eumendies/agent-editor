package com.agent.editor.agent.v2.core.agent;

/**
 * 统一的 agent 决策接口。
 * runtime 只依赖这个抽象，不关心具体实现是 ReAct、planner 还是 supervisor。
 */
public interface Agent {
    AgentType type();
}
