package com.agent.editor.agent.v2.core.agent;

import com.agent.editor.agent.v2.core.context.AgentRunContext;

public interface PlanningAgent extends Agent {
    /**
     * 基于当前上下文生成可执行计划。
     *
     * @param agentRunContext 当前 planner 可见的会话状态、文档内容和历史记忆
     * @return 供后续执行阶段消费的结构化计划
     */
    PlanResult createPlan(AgentRunContext agentRunContext);
}
