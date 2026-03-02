package com.agent.editor.agent;

import com.agent.editor.model.*;
import java.util.Map;

public interface AgentExecutor {
    AgentState execute(Document document, String instruction, String sessionId, 
                       AgentMode mode, Integer maxSteps);
    
    AgentMode getMode();
}
