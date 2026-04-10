package com.agent.editor.agent.reflexion;

import com.agent.editor.agent.core.agent.Agent;
import com.agent.editor.agent.core.agent.AgentType;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.runtime.ExecutionRequest;
import com.agent.editor.agent.core.runtime.ExecutionResult;
import com.agent.editor.agent.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.core.state.*;
import com.agent.editor.agent.task.TaskOrchestrator;
import com.agent.editor.agent.task.TaskRequest;
import com.agent.editor.agent.task.TaskResult;
import com.agent.editor.agent.tool.ExecutionToolAccessPolicy;
import com.agent.editor.agent.tool.ExecutionToolAccessRole;
import com.agent.editor.agent.tool.document.DocumentToolAccessPolicy;
import com.agent.editor.agent.tool.document.DocumentToolMode;

public class ReflexionOrchestrator implements TaskOrchestrator {

    private final ExecutionRuntime runtime;
    private final Agent actorDefinition;
    private final ReflexionCritic criticDefinition;
    private final ReflexionActorContextFactory actorContextFactory;
    private final ReflexionCriticContextFactory criticContextFactory;
    private final ExecutionToolAccessPolicy executionToolAccessPolicy;
    private final DocumentToolAccessPolicy documentToolAccessPolicy;

    public ReflexionOrchestrator(ExecutionRuntime runtime,
                                 Agent actorDefinition,
                                 ReflexionCritic criticDefinition,
                                 ReflexionActorContextFactory actorContextFactory,
                                 ReflexionCriticContextFactory criticContextFactory,
                                 ExecutionToolAccessPolicy executionToolAccessPolicy,
                                 DocumentToolAccessPolicy documentToolAccessPolicy) {
        this.runtime = runtime;
        this.actorDefinition = actorDefinition;
        this.criticDefinition = criticDefinition;
        this.actorContextFactory = actorContextFactory;
        this.criticContextFactory = criticContextFactory;
        this.executionToolAccessPolicy = executionToolAccessPolicy;
        this.documentToolAccessPolicy = documentToolAccessPolicy;
    }

    /**
     * 执行 actor -> critic 的反思循环，直到 critic 放行或达到轮次上限。
     *
     * @param request 任务输入
     * @return 最终文档内容以及 actor 侧累积记忆
     */
    @Override
    public TaskResult execute(TaskRequest request) {
        // actor state 跨轮复用，保存上一轮真正沉淀下来的编辑上下文与 critique 历史。
        AgentRunContext actorState = actorContextFactory.prepareInitialContext(request);
        String currentContent = request.getDocument().getContent();

        for (int round = 1; round <= request.getMaxIterations(); round++) {
            ExecutionResult actorResult = runtime.run(
                    actorDefinition,
                    actorRequest(request, currentContent),
                    actorState.withStage(ExecutionStage.RUNNING)
            );
            actorState = actorResult.getFinalState();
            currentContent = actorResult.getFinalContent();
            ExecutionResult<ReflexionCritique> criticResult = runtime.run(
                    criticDefinition,
                    criticRequest(request, currentContent),
                    // critic 每轮 fresh，避免把上轮批评过程本身继续带进下一轮判定。
                    criticContextFactory.prepareReviewContext(request, actorState, actorResult.getFinalMessage())
            );
            ReflexionCritique critique = criticResult.getResult();
            if (critique.getVerdict() == ReflexionVerdict.PASS) {
                return new TaskResult(TaskStatus.COMPLETED, currentContent, actorState.getMemory());
            }

            // critique 作为新的 user message 回灌给 actor，下一轮由 actor 自己决定如何修正。
            actorState = actorContextFactory.prepareRevisionContext(request, actorState, round, critique);
        }

        return new TaskResult(TaskStatus.COMPLETED, currentContent, actorState.getMemory());
    }

    private ExecutionRequest actorRequest(TaskRequest request, String currentContent) {
        DocumentSnapshot currentDocument = new DocumentSnapshot(
                request.getDocument().getDocumentId(),
                request.getDocument().getTitle(),
                currentContent
        );
        DocumentToolMode documentToolMode = documentToolAccessPolicy.resolveMode(currentDocument);
        ExecutionRequest executionRequest = new ExecutionRequest(
                request.getTaskId(),
                request.getSessionId(),
                AgentType.REFLEXION,
                currentDocument,
                request.getInstruction(),
                request.getMaxIterations(),
                executionToolAccessPolicy.allowedTools(documentToolMode, ExecutionToolAccessRole.MAIN_WRITE)
        );
        executionRequest.setUserProfileGuidance(request.getUserProfileGuidance());
        executionRequest.setDocumentToolMode(documentToolMode);
        return executionRequest;
    }

    private ExecutionRequest criticRequest(TaskRequest request, String currentContent) {
        DocumentSnapshot currentDocument = new DocumentSnapshot(
                request.getDocument().getDocumentId(),
                request.getDocument().getTitle(),
                currentContent
        );
        DocumentToolMode documentToolMode = documentToolAccessPolicy.resolveMode(currentDocument);
        ExecutionRequest executionRequest = new ExecutionRequest(
                request.getTaskId(),
                request.getSessionId(),
                AgentType.REFLEXION,
                currentDocument,
                "critic current content",
                request.getMaxIterations(),
                executionToolAccessPolicy.allowedTools(documentToolMode, ExecutionToolAccessRole.REVIEW)
        );
        executionRequest.setUserProfileGuidance(request.getUserProfileGuidance());
        executionRequest.setDocumentToolMode(documentToolMode);
        return executionRequest;
    }

}
