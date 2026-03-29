package com.agent.editor.agent.v2.core.context;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@AllArgsConstructor
@SuperBuilder
public class SupervisorContext extends AgentRunContext {
    private List<WorkerDefinition> availableWorkers = List.of();
    private List<WorkerResult> workerResults = List.of();

    public void setAvailableWorkers(List<WorkerDefinition> availableWorkers) {
        this.availableWorkers = availableWorkers == null ? List.of() : List.copyOf(availableWorkers);
    }

    public void setWorkerResults(List<WorkerResult> workerResults) {
        this.workerResults = workerResults == null ? List.of() : List.copyOf(workerResults);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class WorkerDefinition {
        private String workerId;
        private String role;
        private String description;
        private Agent agent;
        private List<String> allowedTools = List.of();
        private List<String> capabilities = List.of();

        public WorkerDefinition(String workerId,
                                String role,
                                String description,
                                Agent agent,
                                List<String> allowedTools) {
            this(workerId, role, description, agent, allowedTools, List.of());
        }

        public void setAllowedTools(List<String> allowedTools) {
            this.allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        }

        public void setCapabilities(List<String> capabilities) {
            // capability 标签会被 supervisor 用来做候选筛选，因此这里固定成不可变快照。
            this.capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class WorkerResult {
        private String workerId;
        private TaskStatus status;
        private String summary;
        private String updatedContent;
    }
}
