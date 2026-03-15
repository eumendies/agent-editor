package com.agent.editor.agent.v2.orchestration;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.definition.SupervisorAgentDefinition;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import com.agent.editor.agent.v2.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.runtime.ExecutionResult;
import com.agent.editor.agent.v2.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.agent.v2.trace.TraceCategory;
import com.agent.editor.agent.v2.trace.TraceCollector;
import com.agent.editor.agent.v2.trace.TraceRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

/**
 * 多 agent 编排入口：supervisor 决定下一个 worker，runtime 执行 worker，最后再由 supervisor 汇总。
 */
public class SupervisorOrchestrator implements TaskOrchestrator {

    private final SupervisorAgentDefinition supervisorAgent;
    private final WorkerRegistry workerRegistry;
    private final ExecutionRuntime executionRuntime;
    private final EventPublisher eventPublisher;
    private final TraceCollector traceCollector;

    public SupervisorOrchestrator(SupervisorAgentDefinition supervisorAgent,
                                  WorkerRegistry workerRegistry,
                                  ExecutionRuntime executionRuntime,
                                  EventPublisher eventPublisher,
                                  TraceCollector traceCollector) {
        this.supervisorAgent = supervisorAgent;
        this.workerRegistry = workerRegistry;
        this.executionRuntime = executionRuntime;
        this.eventPublisher = eventPublisher;
        this.traceCollector = traceCollector;
    }

    @Override
    public TaskResult execute(TaskRequest request) {
        String currentContent = request.document().content();
        List<WorkerResult> workerResults = new ArrayList<>();
        // 调度预算至少覆盖“一轮 worker 池 + 一次最终收口”，避免 maxIterations 太小时无法完成监督流程。
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

                // supervisor 只下发子任务，真正的执行仍然复用统一 runtime。
                eventPublisher.publish(new ExecutionEvent(
                        EventType.WORKER_SELECTED,
                        request.taskId(),
                        worker.workerId()
                ));
                traceCollector.collect(new TraceRecord(
                        UUID.randomUUID().toString(),
                        request.taskId(),
                        Instant.now(),
                        TraceCategory.ORCHESTRATION_DECISION,
                        "supervisor.worker.assigned",
                        request.agentType(),
                        worker.workerId(),
                        i,
                        Map.of(
                                "workerId", worker.workerId(),
                                "instruction", assignWorker.instruction(),
                                "reasoning", assignWorker.reasoning(),
                                "allowedTools", worker.allowedTools()
                        )
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
                traceCollector.collect(new TraceRecord(
                        UUID.randomUUID().toString(),
                        request.taskId(),
                        Instant.now(),
                        TraceCategory.ORCHESTRATION_DECISION,
                        "supervisor.worker.completed",
                        request.agentType(),
                        worker.workerId(),
                        i,
                        Map.of(
                                "workerId", worker.workerId(),
                                "summary", result.finalMessage(),
                                "content", result.finalContent()
                        )
                ));
                continue;
            }

            if (decision instanceof SupervisorDecision.Complete complete) {
                eventPublisher.publish(new ExecutionEvent(
                        EventType.SUPERVISOR_COMPLETED,
                        request.taskId(),
                        complete.summary()
                ));
                traceCollector.collect(new TraceRecord(
                        UUID.randomUUID().toString(),
                        request.taskId(),
                        Instant.now(),
                        TraceCategory.ORCHESTRATION_DECISION,
                        "supervisor.completed",
                        request.agentType(),
                        null,
                        i,
                        Map.of(
                                "summary", complete.summary(),
                                "finalContent", complete.finalContent(),
                                "reasoning", complete.reasoning()
                        )
                ));
                return new TaskResult(TaskStatus.COMPLETED, complete.finalContent());
            }
        }

        throw new IllegalStateException("Supervisor terminated without completion");
    }
}
