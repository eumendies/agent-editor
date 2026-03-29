package com.agent.editor.agent.v2.core.agent;

import com.agent.editor.agent.v2.core.context.AgentRunContext;

public interface PlanningAgent extends Agent {
    PlanResult createPlan(AgentRunContext agentRunContext);
}
