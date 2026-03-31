package com.agent.editor.agent.v2.reflexion;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.state.*;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.agent.v2.task.TaskResult;
import com.agent.editor.agent.v2.tool.document.DocumentToolNames;
import java.util.List;

public class ReflexionOrchestrator implements TaskOrchestrator {

    private static final List<String> ACTOR_ALLOWED_TOOLS = List.of(
            DocumentToolNames.EDIT_DOCUMENT,
            DocumentToolNames.APPEND_TO_DOCUMENT,
            DocumentToolNames.GET_DOCUMENT_SNAPSHOT,
            DocumentToolNames.SEARCH_CONTENT
    );
    private static final List<String> CRITIC_ALLOWED_TOOLS = List.of(
            DocumentToolNames.SEARCH_CONTENT,
            DocumentToolNames.ANALYZE_DOCUMENT
    );

    private final ExecutionRuntime runtime;
    private final Agent actorDefinition;
    private final ReflexionCritic criticDefinition;
    private final ReflexionActorContextFactory actorContextFactory;
    private final ReflexionCriticContextFactory criticContextFactory;

    public ReflexionOrchestrator(ExecutionRuntime runtime,
                                 Agent actorDefinition,
                                 ReflexionCritic criticDefinition) {
        this(runtime, actorDefinition, criticDefinition, new ReflexionActorContextFactory(), new ReflexionCriticContextFactory());
    }

    public ReflexionOrchestrator(ExecutionRuntime runtime,
                                 Agent actorDefinition,
                                 ReflexionCritic criticDefinition,
                                 ReflexionActorContextFactory actorContextFactory,
                                 ReflexionCriticContextFactory criticContextFactory) {
        this.runtime = runtime;
        this.actorDefinition = actorDefinition;
        this.criticDefinition = criticDefinition;
        this.actorContextFactory = actorContextFactory;
        this.criticContextFactory = criticContextFactory;
    }

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
        return new ExecutionRequest(
                request.getTaskId(),
                request.getSessionId(),
                AgentType.REFLEXION,
                new DocumentSnapshot(request.getDocument().getDocumentId(), request.getDocument().getTitle(), currentContent),
                request.getInstruction(),
                request.getMaxIterations(),
                ACTOR_ALLOWED_TOOLS
        );
    }

    private ExecutionRequest criticRequest(TaskRequest request, String currentContent) {
        return new ExecutionRequest(
                request.getTaskId(),
                request.getSessionId(),
                AgentType.REFLEXION,
                new DocumentSnapshot(request.getDocument().getDocumentId(), request.getDocument().getTitle(), currentContent),
                "critic current content",
                request.getMaxIterations(),
                CRITIC_ALLOWED_TOOLS
        );
    }

}
