package com.agent.editor.agent;

import com.agent.editor.model.AgentMode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class AgentFactory {
    
    private final Map<AgentMode, AgentExecutor> agentMap = new HashMap<>();

    public AgentFactory(ReActAgent reactAgent, PlanningAgent planningAgent) {
        agentMap.put(AgentMode.REACT, reactAgent);
        agentMap.put(AgentMode.PLANNING, planningAgent);
    }

    public AgentExecutor getAgent(AgentMode mode) {
        AgentExecutor agent = agentMap.get(mode);
        if (agent == null) {
            return agentMap.get(AgentMode.REACT);
        }
        return agent;
    }

    public AgentExecutor getDefaultAgent() {
        return agentMap.get(AgentMode.REACT);
    }

    public boolean isModeSupported(AgentMode mode) {
        return agentMap.containsKey(mode);
    }
}
