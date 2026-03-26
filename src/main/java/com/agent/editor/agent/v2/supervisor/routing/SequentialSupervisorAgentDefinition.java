package com.agent.editor.agent.v2.supervisor.routing;

import com.agent.editor.agent.v2.supervisor.SupervisorAgentDefinition;
import com.agent.editor.agent.v2.supervisor.SupervisorContext;
import com.agent.editor.agent.v2.supervisor.SupervisorDecision;
import com.agent.editor.agent.v2.supervisor.worker.WorkerDefinition;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 第一版 supervisor 策略：按 registry 顺序串行调度尚未执行过的 worker，最后统一汇总。
 */
public class SequentialSupervisorAgentDefinition implements SupervisorAgentDefinition {

    @Override
    public SupervisorDecision decide(SupervisorContext context) {
        // 当前实现是确定性的顺序调度器：先找还没执行过的 worker，最后再统一收口。
        Set<String> completedWorkers = context.workerResults().stream()
                .map(result -> result.workerId())
                .collect(Collectors.toSet());

        // 先把每个 worker 跑完一轮，后续如果要升级成 LLM supervisor，可以直接替换这个策略实现。
        for (WorkerDefinition worker : context.availableWorkers()) {
            if (!completedWorkers.contains(worker.workerId())) {
                String instruction = worker.role() + ": " + worker.description() + "\nTask: " + context.originalInstruction();
                return new SupervisorDecision.AssignWorker(
                        worker.workerId(),
                        instruction,
                        "delegate to " + worker.workerId()
                );
            }
        }

        String summary = context.workerResults().stream()
                .map(result -> result.workerId() + ": " + result.summary())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("No worker steps executed");

        // supervisor 自己负责最终收口，worker 只产出中间结果。
        return new SupervisorDecision.Complete(
                context.currentContent(),
                summary,
                "all workers completed"
        );
    }
}
