package com.agent.editor.agent.v2.core.context;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.agent.v2.tool.ExecutionToolAccessRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * supervisor 执行时使用的上下文，额外携带可调度 worker 及其执行结果。
 */
@Data
@SuperBuilder
public class SupervisorContext extends AgentRunContext {
    // 当前 supervisor 可选择分派的 worker 列表。
    private List<WorkerDefinition> availableWorkers = List.of();
    // 已执行 worker 返回的结果集合。
    private List<WorkerResult> workerResults = List.of();

    public void setAvailableWorkers(List<WorkerDefinition> availableWorkers) {
        this.availableWorkers = availableWorkers == null ? List.of() : List.copyOf(availableWorkers);
    }

    public void setWorkerResults(List<WorkerResult> workerResults) {
        this.workerResults = workerResults == null ? List.of() : List.copyOf(workerResults);
    }

    @Data
    @NoArgsConstructor
    public static class WorkerDefinition {
        // worker 唯一标识。
        private String workerId;
        // worker 在编排中的职责名称。
        private String role;
        // 对 worker 能力和适用场景的文字描述。
        private String description;
        // 实际绑定的 agent 实例。
        private Agent agent;
        // 供 supervisor 做筛选和匹配的能力标签。
        private List<String> capabilities = List.of();
        // worker 在 execution/tool policy 里的显式访问角色。
        private ExecutionToolAccessRole executionToolAccessRole;

        public WorkerDefinition(String workerId,
                                String role,
                                String description,
                                Agent agent,
                                List<String> capabilities) {
            this(workerId, role, description, agent, capabilities, null);
        }

        public WorkerDefinition(String workerId,
                                String role,
                                String description,
                                Agent agent,
                                List<String> capabilities,
                                ExecutionToolAccessRole executionToolAccessRole) {
            this.workerId = workerId;
            this.role = role;
            this.description = description;
            this.agent = agent;
            setCapabilities(capabilities);
            this.executionToolAccessRole = executionToolAccessRole;
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
        // 产出该结果的 worker ID。
        private String workerId;
        // worker 执行结束后的状态。
        private TaskStatus status;
        // 便于 supervisor 汇总的结果摘要。
        private String summary;
        // worker 执行后给出的正文更新结果。
        private String updatedContent;
    }
}
