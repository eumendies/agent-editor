package com.agent.editor.agent.v2.reflexion;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.runtime.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.state.*;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.agent.v2.task.TaskResult;
import org.checkerframework.checker.units.qual.A;

import java.util.ArrayList;
import java.util.List;

public class ReflexionOrchestrator implements TaskOrchestrator {

    private static final List<String> ACTOR_ALLOWED_TOOLS = List.of("editDocument", "searchContent");
    private static final List<String> CRITIC_ALLOWED_TOOLS = List.of("searchContent", "analyzeDocument");

    private final ExecutionRuntime runtime;
    private final Agent actorDefinition;
    private final ReflexionCritic criticDefinition;

    public ReflexionOrchestrator(ExecutionRuntime runtime,
                                 Agent actorDefinition,
                                 ReflexionCritic criticDefinition) {
        this.runtime = runtime;
        this.actorDefinition = actorDefinition;
        this.criticDefinition = criticDefinition;
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
            ExecutionResult<ReflexionCritique> criticResult = runtime.run(
                    criticDefinition,
                    criticRequest(request, currentContent),
                    // critic 每轮 fresh，避免把上轮批评过程本身继续带进下一轮判定。
                    prepareCriticContext(actorState, actorResult.getFinalMessage())
            );
            ReflexionCritique critique = criticResult.getResult();
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

    private AgentRunContext prepareCriticContext(AgentRunContext state, String actorSummary) {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage.UserChatMessage(state.getRequest().getInstruction()));
        messages.add(new ChatMessage.AiChatMessage("""
                Current Content:
                %s
                Actor Summary:
                %s
               """.formatted(state.getCurrentContent(), actorSummary)));

        return new AgentRunContext(
                state.getRequest(),
                state.getIteration(),
                state.getCurrentContent(),
                new ChatTranscriptMemory(messages),
                ExecutionStage.RUNNING,
                state.getPendingReason(),
                state.getToolSpecifications()
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
