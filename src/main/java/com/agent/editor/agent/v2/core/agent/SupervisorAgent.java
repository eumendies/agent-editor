package com.agent.editor.agent.v2.core.agent;

import com.agent.editor.agent.v2.core.context.SupervisorContext;

public interface SupervisorAgent extends Agent {
    SupervisorDecision decide(SupervisorContext context);
}
