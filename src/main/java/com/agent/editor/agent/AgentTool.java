package com.agent.editor.agent;

import java.util.List;
import java.util.Map;

public interface AgentTool {
    String getName();
    String getDescription();
    Map<String, Object> getParameters();
    String execute(Map<String, Object> parameters);
    List<String> getRequiredParameters();
}
