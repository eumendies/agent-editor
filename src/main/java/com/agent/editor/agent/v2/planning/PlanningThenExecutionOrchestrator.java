package com.agent.editor.agent.v2.planning;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.core.state.ExecutionState;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.agent.v2.task.TaskResult;
import com.agent.editor.agent.v2.trace.TraceCategory;
import com.agent.editor.agent.v2.trace.TraceCollector;
import com.agent.editor.agent.v2.trace.TraceRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * 两阶段编排：先由 planner 拆任务，再把每个 plan step 交给执行 agent 串行落地。
 */
public class PlanningThenExecutionOrchestrator implements TaskOrchestrator {

    private final PlanningAgentDefinition planningAgent;
    private final ExecutionRuntime executionRuntime;
    private final AgentDefinition executionAgent;
    private final EventPublisher eventPublisher;
    private final TraceCollector traceCollector;

    public PlanningThenExecutionOrchestrator(PlanningAgentDefinition planningAgent,
                                             ExecutionRuntime executionRuntime,
                                             AgentDefinition executionAgent,
                                             EventPublisher eventPublisher,
                                             TraceCollector traceCollector) {
        this.planningAgent = planningAgent;
        this.executionRuntime = executionRuntime;
        this.executionAgent = executionAgent;
        this.eventPublisher = eventPublisher;
        this.traceCollector = traceCollector;
    }

    @Override
    public TaskResult execute(TaskRequest request) {
        // planning 阶段只生成计划，不直接落文档；真正写文档仍然走统一 execution runtime。
        PlanResult plan = planningAgent.createPlan(request.document(), request.instruction());
        eventPublisher.publish(new ExecutionEvent(
                EventType.PLAN_CREATED,
                request.taskId(),
                "plan created with %d step(s)".formatted(plan.steps().size())
        ));
        traceCollector.collect(new TraceRecord(
                UUID.randomUUID().toString(),
                request.taskId(),
                Instant.now(),
                TraceCategory.ORCHESTRATION_DECISION,
                "planning.plan.created",
                request.agentType(),
                null,
                null,
                Map.of(
                        "plan", plan.steps().stream().map(step -> step.order() + ". " + step.instruction()).toList(),
                        "instruction", request.instruction()
                )
        ));

        ExecutionState currentState = new ExecutionState(
                0,
                request.document().content(),
                request.memory(),
                ExecutionStage.RUNNING,
                null
        );
        String currentContent = request.document().content();
        for (PlanStep step : plan.steps()) {
            traceCollector.collect(new TraceRecord(
                    UUID.randomUUID().toString(),
                    request.taskId(),
                    Instant.now(),
                    TraceCategory.ORCHESTRATION_DECISION,
                    "planning.step.dispatch",
                    request.agentType(),
                    null,
                    step.order(),
                    Map.of(
                            "stepOrder", step.order(),
                            "stepInstruction", step.instruction(),
                            "currentContent", currentContent
                    )
            ));
            ExecutionState stepState = prepareStepState(currentState, step);
            // 每个步骤都基于上一步的文档内容继续执行，形成显式的阶段性产物传递。
            ExecutionResult result = executionRuntime.run(
                    executionAgent,
                    new ExecutionRequest(
                            request.taskId(),
                            request.sessionId(),
                            AgentType.REACT,
                            new DocumentSnapshot(
                                    request.document().documentId(),
                                    request.document().title(),
                                    currentContent
                            ),
                            step.instruction(),
                            request.maxIterations()
                    ),
                    stepState
            );
            // 每一步都把上一步的产物作为新的文档输入，形成显式串行阶段链。
            currentContent = result.finalContent();
            currentState = result.finalState();
        }

        return new TaskResult(TaskStatus.COMPLETED, currentContent, currentState.memory());
    }

    private ExecutionState prepareStepState(ExecutionState state, PlanStep step) {
        if (!(state.memory() instanceof ChatTranscriptMemory transcriptMemory)) {
            return new ExecutionState(
                    state.iteration(),
                    state.currentContent(),
                    state.memory(),
                    ExecutionStage.RUNNING,
                    state.pendingReason()
            );
        }

        ArrayList<ChatMessage> messages = new ArrayList<>(transcriptMemory.messages());
        messages.add(new ChatMessage.UserChatMessage("Plan step %d: %s".formatted(step.order(), step.instruction())));
        return new ExecutionState(
                state.iteration(),
                state.currentContent(),
                new ChatTranscriptMemory(messages),
                ExecutionStage.RUNNING,
                state.pendingReason()
        );
    }
}
