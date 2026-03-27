package com.agent.editor.agent.v2.reflexion;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.runtime.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.state.*;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.agent.v2.task.TaskResult;
import java.util.List;

public class ReflexionOrchestrator implements TaskOrchestrator {

    private static final List<String> ACTOR_ALLOWED_TOOLS = List.of("editDocument", "searchContent");
    private static final List<String> CRITIC_ALLOWED_TOOLS = List.of("searchContent", "analyzeDocument");

    private final ExecutionRuntime runtime;
    private final AgentDefinition actorDefinition;
    private final ReflexionCriticDefinition criticDefinition;
    private final EventPublisher eventPublisher;

    public ReflexionOrchestrator(ExecutionRuntime runtime,
                                 AgentDefinition actorDefinition,
                                 ReflexionCriticDefinition criticDefinition,
                                 EventPublisher eventPublisher) {
        this.runtime = runtime;
        this.actorDefinition = actorDefinition;
        this.criticDefinition = criticDefinition;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public TaskResult execute(TaskRequest request) {
        // actor state 跨轮复用，保存上一轮真正沉淀下来的编辑上下文与 critique 历史。
        AgentRunContext actorState = new AgentRunContext(
                null,
                0,
                request.getDocument().getContent(),
                request.getMemory(),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );
        String currentContent = request.getDocument().getContent();

        for (int round = 1; round <= request.getMaxIterations(); round++) {
            ExecutionResult actorResult = runtime.run(
                    actorDefinition,
                    actorRequest(request, currentContent),
                    actorState.withStage(ExecutionStage.RUNNING)
            );
            actorState = actorResult.getFinalState();
            currentContent = actorResult.getFinalContent();
            ExecutionResult criticResult = runtime.run(
                    criticDefinition,
                    criticRequest(request, currentContent, actorResult.getFinalMessage()),
                    // critic 每轮 fresh，避免把上轮批评过程本身继续带进下一轮判定。
                    new AgentRunContext(0, currentContent)
            );
            ReflexionCritique critique = criticDefinition.parseCritique(criticResult.getFinalMessage());
            if (critique.getVerdict() == ReflexionVerdict.PASS) {
                return new TaskResult(TaskStatus.COMPLETED, currentContent, actorState.getMemory());
            }

            actorState = actorState
                    // critique 作为新的 user message 回灌给 actor，下一轮由 actor 自己决定如何修正。
                    .appendMemory(new ChatMessage.UserChatMessage(formatCritique(round, critique)))
                    .withStage(ExecutionStage.RUNNING);
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

    private ExecutionRequest criticRequest(TaskRequest request, String currentContent, String actorSummary) {
        return new ExecutionRequest(
                request.getTaskId(),
                request.getSessionId(),
                AgentType.REFLEXION,
                new DocumentSnapshot(request.getDocument().getDocumentId(), request.getDocument().getTitle(), currentContent),
                // critic 看的是“原始目标 + actor 本轮摘要”，而不是直接继承 actor 的完整指令链。
                """
                Original instruction:
                %s

                Actor summary:
                %s
                """.formatted(request.getInstruction(), actorSummary),
                request.getMaxIterations(),
                CRITIC_ALLOWED_TOOLS
        );
    }

    private String formatCritique(int round, ReflexionCritique critique) {
        return """
                Reflection critique:
                {"round":%d,"verdict":"%s","feedback":"%s","reasoning":"%s"}
                """.formatted(
                round,
                critique.getVerdict().name(),
                escapeJson(critique.getFeedback()),
                escapeJson(critique.getReasoning())
        );
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

}
