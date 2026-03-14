package com.agent.editor.agent.v2.definition;

import com.agent.editor.agent.v2.orchestration.SupervisorContext;
import com.agent.editor.agent.v2.orchestration.SupervisorDecision;

public interface SupervisorAgentDefinition {

    SupervisorDecision decide(SupervisorContext context);
}
