package com.agent.editor.agent.v2.supervisor;

public interface SupervisorAgentDefinition {

    SupervisorDecision decide(SupervisorContext context);
}
