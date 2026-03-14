package com.agent.editor.agent.v2.orchestration;

import com.agent.editor.agent.v2.definition.AgentType;
import com.agent.editor.agent.v2.definition.SupervisorAgentDefinition;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import com.agent.editor.agent.v2.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.runtime.ExecutionResult;
import com.agent.editor.agent.v2.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.state.DocumentSnapshot;
import com.agent.editor.agent.v2.state.TaskStatus;

import java.util.ArrayList;
import java.util.List;

public class SupervisorOrchestrator implements TaskOrchestrator {

    private final SupervisorAgentDefinition supervisorAgent;
    private final WorkerRegistry workerRegistry;
    private final ExecutionRuntime executionRuntime;
    private final EventPublisher eventPublisher;

    public SupervisorOrchestrator(SupervisorAgentDefinition supervisorAgent,
                                  WorkerRegistry workerRegistry,
                                  ExecutionRuntime executionRuntime,
                                  EventPublisher eventPublisher) {
        this.supervisorAgent = supervisorAgent;
        this.workerRegistry = workerRegistry;
        this.executionRuntime = executionRuntime;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public TaskResult execute(TaskRequest request) {
        String currentContent = request.document().content();
        List<WorkerResult> workerResults = new ArrayList<>();
        int dispatchBudget = Math.max(request.maxIterations(), workerRegistry.all().size()) + 1;

        for (int i = 0; i < dispatchBudget; i++) {
            SupervisorDecision decision = supervisorAgent.decide(new SupervisorContext(
                    request.taskId(),
                    request.sessionId(),
                    request.instruction(),
                    currentContent,
                    workerRegistry.all(),
                    workerResults
            ));

            if (decision instanceof SupervisorDecision.AssignWorker assignWorker) {
                WorkerDefinition worker = workerRegistry.get(assignWorker.workerId());
                if (worker == null) {
                    throw new IllegalArgumentException("Unknown worker: " + assignWorker.workerId());
                }

                eventPublisher.publish(new ExecutionEvent(
                        EventType.WORKER_SELECTED,
                        request.taskId(),
                        worker.workerId()
                ));

                ExecutionResult result = executionRuntime.run(
                        worker.agentDefinition(),
                        new ExecutionRequest(
                                request.taskId(),
                                request.sessionId(),
                                AgentType.REACT,
                                new DocumentSnapshot(
                                        request.document().documentId(),
                                        request.document().title(),
                                        currentContent
                                ),
                                assignWorker.instruction(),
                                request.maxIterations(),
                                worker.workerId(),
                                worker.allowedTools()
                        )
                );

                currentContent = result.finalContent();
                workerResults.add(new WorkerResult(
                        worker.workerId(),
                        TaskStatus.COMPLETED,
                        result.finalMessage(),
                        result.finalContent()
                ));

                eventPublisher.publish(new ExecutionEvent(
                        EventType.WORKER_COMPLETED,
                        request.taskId(),
                        worker.workerId() + ": " + result.finalMessage()
                ));
                continue;
            }

            if (decision instanceof SupervisorDecision.Complete complete) {
                eventPublisher.publish(new ExecutionEvent(
                        EventType.SUPERVISOR_COMPLETED,
                        request.taskId(),
                        complete.summary()
                ));
                return new TaskResult(TaskStatus.COMPLETED, complete.finalContent());
            }
        }

        throw new IllegalStateException("Supervisor terminated without completion");
    }
}
