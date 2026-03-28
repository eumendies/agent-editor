package com.agent.editor.agent.v2.supervisor;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.runtime.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.agent.v2.task.TaskResult;
import com.agent.editor.agent.v2.supervisor.worker.WorkerDefinition;
import com.agent.editor.agent.v2.supervisor.worker.WorkerRegistry;
import com.agent.editor.agent.v2.supervisor.worker.WorkerResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 多 agent 编排入口：supervisor 决定下一个 worker，runtime 执行 worker，最后再由 supervisor 汇总。
 */
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
        String currentContent = request.getDocument().getContent();
        AgentRunContext conversationState = new AgentRunContext(
                null,
                0,
                currentContent,
                request.getMemory(),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );
        List<WorkerResult> workerResults = new ArrayList<>();
        // 混合 supervisor 允许 worker 重复调度，因此预算至少覆盖“一轮 worker 池 + 一次额外重试 + 最终收口”。
        int dispatchBudget = Math.max(request.getMaxIterations() + 1, workerRegistry.all().size() + 2);

        for (int i = 0; i < dispatchBudget; i++) {
            SupervisorDecision decision = supervisorAgent.decide(new SupervisorContext(
                    request.getTaskId(),
                    request.getSessionId(),
                    request.getInstruction(),
                    currentContent,
                    workerRegistry.all(),
                    workerResults
            ));

            if (decision instanceof SupervisorDecision.AssignWorker assignWorker) {
                // supervisor 只决定“谁来做、做什么”，具体执行仍然完全复用单 agent runtime。
                WorkerDefinition worker = workerRegistry.get(assignWorker.getWorkerId());
                if (worker == null) {
                    throw new IllegalArgumentException("Unknown worker: " + assignWorker.getWorkerId());
                }

                // supervisor 只下发子任务，真正的执行仍然复用统一 runtime。
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
                        buildWorkerRunContext(conversationState, currentContent)
                );

                // worker 执行完后，最新文档内容会回灌给 supervisor，供下一轮继续分派。
                currentContent = result.getFinalContent();
                conversationState = appendWorkerSummary(conversationState, worker.getWorkerId(), result.getFinalMessage())
                        .withCurrentContent(currentContent)
                        .withStage(ExecutionStage.RUNNING);
                workerResults.add(new WorkerResult(
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
                // 最终内容以 supervisor 的收口结果为准，而不是某个 worker 的局部输出。
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

    // worker 之间只共享结构化阶段结果，不共享完整工具 transcript，避免角色决策被上游过程污染。
    private AgentRunContext buildWorkerRunContext(AgentRunContext conversationState, String currentContent) {
        return new AgentRunContext(
                null,
                conversationState.getIteration(),
                currentContent,
                conversationState.getMemory(),
                ExecutionStage.RUNNING,
                conversationState.getPendingReason(),
                List.of()
        );
    }

    private AgentRunContext appendWorkerSummary(AgentRunContext conversationState, String workerId, String summary) {
        return conversationState.appendMemory(new ChatMessage.UserChatMessage("""
                Previous worker result:
                workerId: %s
                summary: %s
                """.formatted(workerId, summary)));
    }
}
