package com.agent.editor.agent.v2.definition;

import com.agent.editor.agent.v2.orchestration.SupervisorContext;
import com.agent.editor.agent.v2.orchestration.SupervisorDecision;
import com.agent.editor.agent.v2.orchestration.WorkerDefinition;

import java.util.Set;
import java.util.stream.Collectors;

public class SequentialSupervisorAgentDefinition implements SupervisorAgentDefinition {

    @Override
    public SupervisorDecision decide(SupervisorContext context) {
        Set<String> completedWorkers = context.workerResults().stream()
                .map(result -> result.workerId())
                .collect(Collectors.toSet());

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

        return new SupervisorDecision.Complete(
                context.currentContent(),
                summary,
                "all workers completed"
        );
    }
}
