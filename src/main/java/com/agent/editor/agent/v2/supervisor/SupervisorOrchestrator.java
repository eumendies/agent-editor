package com.agent.editor.agent.v2.supervisor;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.SupervisorAgent;
import com.agent.editor.agent.v2.core.agent.SupervisorDecision;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.context.SupervisorContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.runtime.SupervisorExecutionRuntime;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import com.agent.editor.agent.v2.supervisor.worker.WorkerRegistry;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.agent.v2.task.TaskResult;
import com.agent.editor.agent.v2.tool.ExecutionToolAccessPolicy;
import com.agent.editor.agent.v2.tool.ExecutionToolAccessRole;
import com.agent.editor.agent.v2.tool.document.DocumentToolAccessPolicy;
import com.agent.editor.agent.v2.tool.document.DocumentToolAccessRole;
import com.agent.editor.agent.v2.tool.document.DocumentToolMode;

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
    private final DocumentToolAccessPolicy documentToolAccessPolicy;
    private final ExecutionToolAccessPolicy executionToolAccessPolicy;

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
                documentToolAccessPolicy,
                null
        );
    }

    public SupervisorOrchestrator(SupervisorAgent supervisorAgent,
                                  SupervisorExecutionRuntime supervisorExecutionRuntime,
                                  WorkerRegistry workerRegistry,
                                  ExecutionRuntime executionRuntime,
                                  EventPublisher eventPublisher,
                                  SupervisorContextFactory supervisorContextFactory,
                                  DocumentToolAccessPolicy documentToolAccessPolicy,
                                  ExecutionToolAccessPolicy executionToolAccessPolicy) {
        this.supervisorAgent = supervisorAgent;
        this.supervisorExecutionRuntime = supervisorExecutionRuntime;
        this.workerRegistry = workerRegistry;
        this.executionRuntime = executionRuntime;
        this.eventPublisher = eventPublisher;
        this.supervisorContextFactory = supervisorContextFactory;
        this.documentToolAccessPolicy = documentToolAccessPolicy;
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
                DocumentToolMode documentToolMode = resolveWorkerDocumentToolMode(worker, workerDocument);
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
                                worker,
                                documentToolMode
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
                                                    SupervisorContext.WorkerDefinition worker,
                                                    DocumentToolMode documentToolMode) {
        ExecutionRequest executionRequest = new ExecutionRequest(
                taskId,
                sessionId,
                AgentType.REACT,
                workerDocument,
                instruction,
                maxIterations,
                workerId,
                resolveWorkerAllowedTools(worker, documentToolMode)
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
        if (isMemoryWorker(worker) && executionToolAccessPolicy != null) {
            // memory worker 需要走 execution 级别的组合策略，才能拿到 memory tool，同时继续复用统一白名单裁剪。
            return executionToolAccessPolicy.allowedTools(documentToolMode, ExecutionToolAccessRole.MEMORY);
        }
        DocumentToolAccessRole role = workerAccessRole(worker);
        if (role == null) {
            return worker.getAllowedTools();
        }
        return documentToolAccessPolicy.allowedTools(documentToolMode, role);
    }

    private DocumentToolMode resolveWorkerDocumentToolMode(SupervisorContext.WorkerDefinition worker,
                                                           DocumentSnapshot currentDocument) {
        DocumentToolAccessRole role = workerAccessRole(worker);
        if (role == null || role == DocumentToolAccessRole.RESEARCH) {
            return DocumentToolMode.FULL;
        }
        return documentToolAccessPolicy.resolveMode(currentDocument);
    }

    private DocumentToolAccessRole workerAccessRole(SupervisorContext.WorkerDefinition worker) {
        List<String> capabilities = worker.getCapabilities();
        // capability 驱动角色识别，避免后续增加同类 worker 时还要同步维护一份硬编码 ID 白名单。
        if (capabilities.contains("research")) {
            return DocumentToolAccessRole.RESEARCH;
        }
        if (capabilities.contains("review")) {
            return DocumentToolAccessRole.REVIEW;
        }
        if (capabilities.contains("write") || capabilities.contains("edit")) {
            return DocumentToolAccessRole.WRITE;
        }
        return null;
    }

    private boolean isMemoryWorker(SupervisorContext.WorkerDefinition worker) {
        return worker.getCapabilities().contains("memory");
    }
}
