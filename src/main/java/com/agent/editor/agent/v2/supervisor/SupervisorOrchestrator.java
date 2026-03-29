package com.agent.editor.agent.v2.supervisor;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.SupervisorAgent;
import com.agent.editor.agent.v2.core.agent.SupervisorDecision;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.context.SupervisorContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import com.agent.editor.agent.v2.supervisor.worker.WorkerRegistry;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.agent.v2.task.TaskResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 多 agent 编排入口：supervisor 决定下一个 worker，runtime 执行 worker，最后再由 supervisor 汇总。
 */
public class SupervisorOrchestrator implements TaskOrchestrator {

    private final SupervisorAgent supervisorAgent;
    private final WorkerRegistry workerRegistry;
    private final ExecutionRuntime executionRuntime;
    private final EventPublisher eventPublisher;
    private final SupervisorContextFactory supervisorContextFactory;

    public SupervisorOrchestrator(SupervisorAgent supervisorAgent,
                                  WorkerRegistry workerRegistry,
                                  ExecutionRuntime executionRuntime,
                                  EventPublisher eventPublisher,
                                  SupervisorContextFactory supervisorContextFactory) {
        this.supervisorAgent = supervisorAgent;
        this.workerRegistry = workerRegistry;
        this.executionRuntime = executionRuntime;
        this.eventPublisher = eventPublisher;
        this.supervisorContextFactory = supervisorContextFactory;
    }

    @Override
    public TaskResult execute(TaskRequest request) {
        AgentRunContext conversationState = supervisorContextFactory.prepareInitialContext(request);
        String currentContent = conversationState.getCurrentContent();
        List<SupervisorContext.WorkerResult> workerResults = new ArrayList<>();
        // 混合 supervisor 允许 worker 重复调度，因此预算至少覆盖“一轮 worker 池 + 一次额外重试 + 最终收口”。
        int dispatchBudget = Math.max(request.getMaxIterations() + 1, workerRegistry.all().size() + 2);

        for (int i = 0; i < dispatchBudget; i++) {
            SupervisorContext supervisorContext = supervisorContextFactory.buildSupervisorContext(
                    request,
                    conversationState,
                    workerResults,
                    workerRegistry.all()
            );
            SupervisorDecision decision = supervisorAgent.decide(supervisorContext);

            if (decision instanceof SupervisorDecision.AssignWorker assignWorker) {
                SupervisorContext.WorkerDefinition worker = workerRegistry.get(assignWorker.getWorkerId());
                if (worker == null) {
                    throw new IllegalArgumentException("Unknown worker: " + assignWorker.getWorkerId());
                }

                eventPublisher.publish(new ExecutionEvent(
                        EventType.WORKER_SELECTED,
                        request.getTaskId(),
                        worker.getWorkerId()
                ));

                ExecutionResult result = executionRuntime.run(
                        worker.getAgent(),
                        new ExecutionRequest(
                                request.getTaskId(),
                                request.getSessionId(),
                                AgentType.REACT,
                                new DocumentSnapshot(
                                        request.getDocument().getDocumentId(),
                                        request.getDocument().getTitle(),
                                        currentContent
                                ),
                                assignWorker.getInstruction(),
                                request.getMaxIterations(),
                                worker.getWorkerId(),
                                worker.getAllowedTools()
                        ),
                        supervisorContextFactory.buildWorkerExecutionContext(conversationState, currentContent)
                );

                currentContent = result.getFinalContent();
                conversationState = supervisorContextFactory.summarizeWorkerResult(
                        conversationState,
                        worker.getWorkerId(),
                        result
                );
                workerResults.add(new SupervisorContext.WorkerResult(
                        worker.getWorkerId(),
                        TaskStatus.COMPLETED,
                        result.getFinalMessage(),
                        result.getFinalContent()
                ));

                eventPublisher.publish(new ExecutionEvent(
                        EventType.WORKER_COMPLETED,
                        request.getTaskId(),
                        worker.getWorkerId() + ": " + result.getFinalMessage()
                ));
                continue;
            }

            if (decision instanceof SupervisorDecision.Complete complete) {
                eventPublisher.publish(new ExecutionEvent(
                        EventType.SUPERVISOR_COMPLETED,
                        request.getTaskId(),
                        complete.getSummary()
                ));
                return new TaskResult(TaskStatus.COMPLETED, complete.getFinalContent(), conversationState.getMemory());
            }
        }

        throw new IllegalStateException("Supervisor terminated without completion");
    }
}
