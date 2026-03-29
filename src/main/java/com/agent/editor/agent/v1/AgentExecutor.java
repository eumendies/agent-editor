package com.agent.editor.agent.v1;

import com.agent.editor.model.*;

public interface AgentExecutor {
    AgentState execute(Document document, String instruction, String sessionId, 
                       AgentMode mode, Integer maxSteps);
    
    AgentMode getMode();
}
