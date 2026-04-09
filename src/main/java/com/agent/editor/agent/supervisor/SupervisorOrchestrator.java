package com.agent.editor.agent.supervisor;

import com.agent.editor.agent.core.agent.AgentType;
import com.agent.editor.agent.core.agent.SupervisorAgent;
import com.agent.editor.agent.core.agent.SupervisorDecision;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.context.SupervisorContext;
import com.agent.editor.agent.core.runtime.ExecutionRequest;
import com.agent.editor.agent.core.runtime.ExecutionResult;
import com.agent.editor.agent.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.core.runtime.SupervisorExecutionRuntime;
import com.agent.editor.agent.core.state.DocumentSnapshot;
import com.agent.editor.agent.core.state.TaskStatus;
import com.agent.editor.agent.event.EventPublisher;
import com.agent.editor.agent.event.EventType;
import com.agent.editor.agent.event.ExecutionEvent;
import com.agent.editor.agent.supervisor.worker.WorkerRegistry;
import com.agent.editor.agent.task.TaskOrchestrator;
import com.agent.editor.agent.task.TaskRequest;
import com.agent.editor.agent.task.TaskResult;
import com.agent.editor.agent.tool.ExecutionToolAccessPolicy;
import com.agent.editor.agent.tool.ExecutionToolAccessRole;
import com.agent.editor.agent.tool.memory.MemoryToolAccessPolicy;
import com.agent.editor.agent.tool.document.DocumentToolAccessPolicy;
import com.agent.editor.agent.tool.document.DocumentToolMode;

import java.util.ArrayList;
import java.util.List;

/**
 * supervisor 编排入口。
 * 关键职责是维护当前文档内容、累计 worker 结果，并在每一轮把最新上下文重新交给 supervisor 做下一跳决策。
 */
public class SupervisorOrchestrator implements TaskOrchestrator {

    private static final int RESEARCHER_MAX_ITERATIONS = 4;

    private final SupervisorAgent supervisorAgent;
    private final SupervisorExecutionRuntime supervisorExecutionRuntime;
    private final WorkerRegistry workerRegistry;
    private final ExecutionRuntime executionRuntime;
    private final EventPublisher eventPublisher;
    private final SupervisorContextFactory supervisorContextFactory;
    private final ExecutionToolAccessPolicy executionToolAccessPolicy;

    /**
     * 兼容旧测试/调用方的过渡构造器。
     * 外部如果已经持有 execution tool policy，就直接复用同一套 mode/tool 解析逻辑。
     */
    public SupervisorOrchestrator(SupervisorAgent supervisorAgent,
                                  SupervisorExecutionRuntime supervisorExecutionRuntime,
                                  WorkerRegistry workerRegistry,
                                  ExecutionRuntime executionRuntime,
                                  EventPublisher eventPublisher,
                                  SupervisorContextFactory supervisorContextFactory,
                                  DocumentToolAccessPolicy documentToolAccessPolicy,
                                  ExecutionToolAccessPolicy executionToolAccessPolicy) {
        this(
                supervisorAgent,
                supervisorExecutionRuntime,
                workerRegistry,
                executionRuntime,
                eventPublisher,
                supervisorContextFactory,
                executionToolAccessPolicy
        );
    }

    /**
     * 兼容旧测试/调用方的过渡构造器。
     * 这里保留原有 document policy 入参，内部补齐默认 execution policy，避免把迁移成本扩散到所有测试。
     */
    public SupervisorOrchestrator(SupervisorAgent supervisorAgent,
                                  SupervisorExecutionRuntime supervisorExecutionRuntime,
                                  WorkerRegistry workerRegistry,
                                  ExecutionRuntime executionRuntime,
                                  EventPublisher eventPublisher,
                                  SupervisorContextFactory supervisorContextFactory,
                                  DocumentToolAccessPolicy documentToolAccessPolicy) {
        this(
                supervisorAgent,
                supervisorExecutionRuntime,
                workerRegistry,
                executionRuntime,
                eventPublisher,
                supervisorContextFactory,
                new ExecutionToolAccessPolicy(documentToolAccessPolicy, new MemoryToolAccessPolicy())
        );
    }

    public SupervisorOrchestrator(SupervisorAgent supervisorAgent,
                                  SupervisorExecutionRuntime supervisorExecutionRuntime,
                                  WorkerRegistry workerRegistry,
                                  ExecutionRuntime executionRuntime,
                                  EventPublisher eventPublisher,
                                  SupervisorContextFactory supervisorContextFactory,
                                  ExecutionToolAccessPolicy executionToolAccessPolicy) {
        this.supervisorAgent = supervisorAgent;
        this.supervisorExecutionRuntime = supervisorExecutionRuntime;
        this.workerRegistry = workerRegistry;
        this.executionRuntime = executionRuntime;
        this.eventPublisher = eventPublisher;
        this.supervisorContextFactory = supervisorContextFactory;
        this.executionToolAccessPolicy = executionToolAccessPolicy;
    }

    /**
     * 驱动 supervisor 与 worker 的多轮协作，直到 supervisor 明确完成任务。
     *
     * @param request 任务输入
     * @return 最终任务结果以及会话记忆摘要
     */
    @Override
    public TaskResult execute(TaskRequest request) {
        // conversationState 保存“对后续 agent 可见的会话态”，currentContent 则单独保留当前文档正文。
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
            ExecutionResult<SupervisorDecision> supervisorResult = supervisorExecutionRuntime.run(
                    supervisorAgent,
                    withUserProfileGuidance(new ExecutionRequest(
                            request.getTaskId(),
                            request.getSessionId(),
                            AgentType.SUPERVISOR,
                            new DocumentSnapshot(
                                    request.getDocument().getDocumentId(),
                                    request.getDocument().getTitle(),
                                    currentContent
                            ),
                            request.getInstruction(),
                            request.getMaxIterations()
                    ), request),
                    supervisorContext
            );
            SupervisorDecision decision = supervisorResult.getResult();

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

                DocumentSnapshot workerDocument = new DocumentSnapshot(
                        request.getDocument().getDocumentId(),
                        request.getDocument().getTitle(),
                        currentContent
                );
                DocumentToolMode documentToolMode = executionToolAccessPolicy.resolveMode(workerDocument);
                ExecutionResult result = executionRuntime.run(
                        worker.getAgent(),
                        workerExecutionRequest(
                                request.getTaskId(),
                                request.getSessionId(),
                                workerDocument,
                                assignWorker.getInstruction(),
                                resolveWorkerMaxIterations(request, worker),
                                request.getUserProfileGuidance(),
                                worker.getWorkerId(),
                                documentToolMode,
                                resolveWorkerAllowedTools(worker, documentToolMode)
                        ),
                        supervisorContextFactory.buildWorkerExecutionContext(
                                conversationState,
                                currentContent,
                                assignWorker.getInstruction()
                        )
                );

                // worker 产出的正文会立即成为下一轮 supervisor 看到的最新文档。
                currentContent = result.getFinalContent();
                // 这里只把 worker 结果压缩成摘要写回记忆，避免把完整工具调用轨迹不断滚雪球式传递下去。
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

    private int resolveWorkerMaxIterations(TaskRequest request, SupervisorContext.WorkerDefinition worker) {
        if (SupervisorWorkerIds.RESEARCHER.equals(worker.getWorkerId())) {
            // researcher 的工具循环以检索收敛为主，给过大的预算只会放大重复改写/重试的概率。
            return Math.min(request.getMaxIterations(), RESEARCHER_MAX_ITERATIONS);
        }
        return request.getMaxIterations();
    }

    private ExecutionRequest workerExecutionRequest(String taskId,
                                                    String sessionId,
                                                    DocumentSnapshot workerDocument,
                                                    String instruction,
                                                    int maxIterations,
                                                    String userProfileGuidance,
                                                    String workerId,
                                                    DocumentToolMode documentToolMode,
                                                    List<String> allowedTools) {
        ExecutionRequest executionRequest = new ExecutionRequest(
                taskId,
                sessionId,
                AgentType.REACT,
                workerDocument,
                instruction,
                maxIterations,
                workerId,
                allowedTools
        );
        executionRequest.setUserProfileGuidance(userProfileGuidance);
        executionRequest.setDocumentToolMode(documentToolMode);
        return executionRequest;
    }

    private ExecutionRequest withUserProfileGuidance(ExecutionRequest executionRequest, TaskRequest taskRequest) {
        executionRequest.setUserProfileGuidance(taskRequest.getUserProfileGuidance());
        return executionRequest;
    }

    private List<String> resolveWorkerAllowedTools(SupervisorContext.WorkerDefinition worker,
                                                   DocumentToolMode documentToolMode) {
        ExecutionToolAccessRole executionToolAccessRole = worker.getExecutionToolAccessRole();
        if (executionToolAccessRole == null) {
            // worker 工具权限完全由 execution role 推导；缺失 role 说明配置不完整，不能退回旧式静态工具名单。
            throw new IllegalStateException("Worker missing executionToolAccessRole: " + worker.getWorkerId());
        }
        return executionToolAccessPolicy.allowedTools(documentToolMode, executionToolAccessRole);
    }
}
