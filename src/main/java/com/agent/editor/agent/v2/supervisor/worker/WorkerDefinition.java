package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class WorkerDefinition {

    private String workerId;
    private String role;
    private String description;
    private AgentDefinition agentDefinition;
    private List<String> allowedTools = List.of();
    private List<String> capabilities = List.of();

    public WorkerDefinition(String workerId,
                            String role,
                            String description,
                            AgentDefinition agentDefinition,
                            List<String> allowedTools,
                            List<String> capabilities) {
        this.workerId = workerId;
        this.role = role;
        this.description = description;
        this.agentDefinition = agentDefinition;
        setAllowedTools(allowedTools);
        setCapabilities(capabilities);
    }

    public WorkerDefinition(String workerId,
                            String role,
                            String description,
                            AgentDefinition agentDefinition,
                            List<String> allowedTools) {
        this(workerId, role, description, agentDefinition, allowedTools, List.of());
    }

    public void setAllowedTools(List<String> allowedTools) {
        this.allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }

    public void setCapabilities(List<String> capabilities) {
        // capability 标签会被 supervisor 用来做候选筛选，因此这里固定成不可变快照。
        this.capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }
}
