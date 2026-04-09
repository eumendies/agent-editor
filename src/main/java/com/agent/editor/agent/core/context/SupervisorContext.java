package com.agent.editor.agent.core.context;

import com.agent.editor.agent.core.agent.Agent;
import com.agent.editor.agent.core.state.TaskStatus;
import com.agent.editor.agent.tool.ExecutionToolAccessRole;
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
        // worker 在 execution/tool policy 里的显式访问角色。
        private ExecutionToolAccessRole executionToolAccessRole;

        public WorkerDefinition(String workerId,
                                String role,
                                String description,
                                Agent agent,
                                ExecutionToolAccessRole executionToolAccessRole) {
            this.workerId = workerId;
            this.role = role;
            this.description = description;
            this.agent = agent;
            this.executionToolAccessRole = executionToolAccessRole;
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
