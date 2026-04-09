package com.agent.editor.agent.core.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * supervisor 在单轮决策后的结构化输出，表示继续分派或直接完成。
 */
public sealed interface SupervisorDecision permits SupervisorDecision.AssignWorker, SupervisorDecision.Complete {

    /**
     * 将当前任务分配给指定 worker 执行。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class AssignWorker implements SupervisorDecision {

        // 被选中的 worker ID。
        private String workerId;
        // 下发给 worker 的具体执行指令。
        private String instruction;
        // supervisor 做出该决策的理由。
        private String reasoning;
    }

    /**
     * supervisor 认为可以直接结束任务并输出结果。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class Complete implements SupervisorDecision {

        // 最终正文内容。
        private String finalContent;
        // 面向上层的结果摘要。
        private String summary;
        // supervisor 判定完成的理由。
        private String reasoning;
    }
}
