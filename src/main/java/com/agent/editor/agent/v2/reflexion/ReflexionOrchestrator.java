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
import com.agent.editor.agent.v2.trace.TraceCategory;
import com.agent.editor.agent.v2.trace.TraceCollector;
import com.agent.editor.agent.v2.trace.TraceRecord;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReflexionOrchestrator implements TaskOrchestrator {

    private static final List<String> ACTOR_ALLOWED_TOOLS = List.of("editDocument", "searchContent");
    private static final List<String> CRITIC_ALLOWED_TOOLS = List.of("searchContent", "analyzeDocument");

    private final ExecutionRuntime runtime;
    private final AgentDefinition actorDefinition;
    private final ReflexionCriticDefinition criticDefinition;
    private final EventPublisher eventPublisher;
    private final TraceCollector traceCollector;

    public ReflexionOrchestrator(ExecutionRuntime runtime,
                                 AgentDefinition actorDefinition,
                                 ReflexionCriticDefinition criticDefinition,
                                 EventPublisher eventPublisher,
                                 TraceCollector traceCollector) {
        this.runtime = runtime;
        this.actorDefinition = actorDefinition;
        this.criticDefinition = criticDefinition;
        this.eventPublisher = eventPublisher;
        this.traceCollector = traceCollector;
    }

    @Override
    public TaskResult execute(TaskRequest request) {
        // actor state 跨轮复用，保存上一轮真正沉淀下来的编辑上下文与 critique 历史。
        AgentRunContext actorState = new AgentRunContext(
                null,
                0,
                request.document().content(),
                request.memory(),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );
        String currentContent = request.document().content();

        for (int round = 1; round <= request.maxIterations(); round++) {
            traceCollector.collect(traceRecord(
                    request,
                    "reflexion.actor.started",
                    round,
                    Map.of("content", currentContent)
            ));
            ExecutionResult actorResult = runtime.run(
                    actorDefinition,
                    actorRequest(request, currentContent),
                    actorState.withStage(ExecutionStage.RUNNING)
            );
            actorState = actorResult.finalState();
            currentContent = actorResult.finalContent();
            traceCollector.collect(traceRecord(
                    request,
                    "reflexion.actor.completed",
                    round,
                    Map.of(
                            "summary", actorResult.finalMessage(),
                            "content", currentContent
                    )
            ));

            traceCollector.collect(traceRecord(
                    request,
                    "reflexion.critic.started",
                    round,
                    Map.of(
                            "content", currentContent,
                            "actorSummary", actorResult.finalMessage()
                    )
            ));
            ExecutionResult criticResult = runtime.run(
                    criticDefinition,
                    criticRequest(request, currentContent, actorResult.finalMessage()),
                    // critic 每轮 fresh，避免把上轮批评过程本身继续带进下一轮判定。
                    new AgentRunContext(0, currentContent)
            );
            ReflexionCritique critique = criticDefinition.parseCritique(criticResult.finalMessage());
            traceCollector.collect(traceRecord(
                    request,
                    "reflexion.critic.completed",
                    round,
                    Map.of(
                            "verdict", critique.verdict().name(),
                            "feedback", critique.feedback(),
                            "reasoning", critique.reasoning()
                    )
            ));
            if (critique.verdict() == ReflexionVerdict.PASS) {
                traceCollector.collect(traceRecord(
                        request,
                        "reflexion.pass",
                        round,
                        Map.of(
                                "feedback", critique.feedback(),
                                "reasoning", critique.reasoning(),
                                "content", currentContent
                        )
                ));
                return new TaskResult(TaskStatus.COMPLETED, currentContent, actorState.memory());
            }

            actorState = actorState
                    // critique 作为新的 user message 回灌给 actor，下一轮由 actor 自己决定如何修正。
                    .appendMemory(new ChatMessage.UserChatMessage(formatCritique(round, critique)))
                    .withStage(ExecutionStage.RUNNING);
            traceCollector.collect(traceRecord(
                    request,
                    "reflexion.revise",
                    round,
                    Map.of(
                            "feedback", critique.feedback(),
                            "reasoning", critique.reasoning()
                    )
            ));
        }

        traceCollector.collect(traceRecord(
                request,
                "reflexion.max.rounds.reached",
                request.maxIterations(),
                Map.of("content", currentContent)
        ));
        return new TaskResult(TaskStatus.COMPLETED, currentContent, actorState.memory());
    }

    private ExecutionRequest actorRequest(TaskRequest request, String currentContent) {
        return new ExecutionRequest(
                request.taskId(),
                request.sessionId(),
                AgentType.REFLEXION,
                new DocumentSnapshot(request.document().documentId(), request.document().title(), currentContent),
                request.instruction(),
                request.maxIterations(),
                ACTOR_ALLOWED_TOOLS
        );
    }

    private ExecutionRequest criticRequest(TaskRequest request, String currentContent, String actorSummary) {
        return new ExecutionRequest(
                request.taskId(),
                request.sessionId(),
                AgentType.REFLEXION,
                new DocumentSnapshot(request.document().documentId(), request.document().title(), currentContent),
                // critic 看的是“原始目标 + actor 本轮摘要”，而不是直接继承 actor 的完整指令链。
                """
                Original instruction:
                %s

                Actor summary:
                %s
                """.formatted(request.instruction(), actorSummary),
                request.maxIterations(),
                CRITIC_ALLOWED_TOOLS
        );
    }

    private String formatCritique(int round, ReflexionCritique critique) {
        return """
                Critique round %d:
                %s

                Reasoning:
                %s
                """.formatted(round, critique.feedback(), critique.reasoning());
    }

    private TraceRecord traceRecord(TaskRequest request,
                                    String stage,
                                    int round,
                                    Map<String, Object> payload) {
        return new TraceRecord(
                UUID.randomUUID().toString(),
                request.taskId(),
                Instant.now(),
                TraceCategory.ORCHESTRATION_DECISION,
                stage,
                request.agentType(),
                null,
                round,
                payload
        );
    }
}
