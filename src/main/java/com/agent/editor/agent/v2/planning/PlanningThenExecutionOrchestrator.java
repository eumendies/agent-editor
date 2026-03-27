package com.agent.editor.agent.v2.planning;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
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
        PlanResult plan = planningAgent.createPlan(request.getDocument(), request.getInstruction());
        eventPublisher.publish(new ExecutionEvent(
                EventType.PLAN_CREATED,
                request.getTaskId(),
                "plan created with %d step(s)".formatted(plan.getSteps().size())
        ));
        traceCollector.collect(new TraceRecord(
                UUID.randomUUID().toString(),
                request.getTaskId(),
                Instant.now(),
                TraceCategory.ORCHESTRATION_DECISION,
                "planning.plan.created",
                request.getAgentType(),
                null,
                null,
                Map.of(
                        "plan", plan.getSteps().stream().map(step -> step.getOrder() + ". " + step.getInstruction()).toList(),
                        "instruction", request.getInstruction()
                )
        ));

        AgentRunContext currentState = new AgentRunContext(
                null,
                0,
                request.getDocument().getContent(),
                request.getMemory(),
                ExecutionStage.RUNNING,
                null,
                java.util.List.of()
        );
        String currentContent = request.getDocument().getContent();
        for (PlanStep step : plan.getSteps()) {
            traceCollector.collect(new TraceRecord(
                    UUID.randomUUID().toString(),
                    request.getTaskId(),
                    Instant.now(),
                    TraceCategory.ORCHESTRATION_DECISION,
                    "planning.step.dispatch",
                    request.getAgentType(),
                    null,
                    step.getOrder(),
                    Map.of(
                            "stepOrder", step.getOrder(),
                            "stepInstruction", step.getInstruction(),
                            "currentContent", currentContent
                    )
            ));
            AgentRunContext stepState = prepareStepState(currentState, step);
            // 每个步骤都基于上一步的文档内容继续执行，形成显式的阶段性产物传递。
            ExecutionResult result = executionRuntime.run(
                    executionAgent,
                    new ExecutionRequest(
                            request.getTaskId(),
                            request.getSessionId(),
                            AgentType.REACT,
                            new DocumentSnapshot(
                                    request.getDocument().getDocumentId(),
                                    request.getDocument().getTitle(),
                                    currentContent
                            ),
                            step.getInstruction(),
                            request.getMaxIterations()
                    ),
                    stepState
            );
            // 每一步都把上一步的产物作为新的文档输入，形成显式串行阶段链。
            currentContent = result.getFinalContent();
            currentState = result.getFinalState();
        }

        return new TaskResult(TaskStatus.COMPLETED, currentContent, currentState.getMemory());
    }

    private AgentRunContext prepareStepState(AgentRunContext state, PlanStep step) {
        if (!(state.getMemory() instanceof ChatTranscriptMemory transcriptMemory)) {
            return new AgentRunContext(
                    state.getRequest(),
                    state.getIteration(),
                    state.getCurrentContent(),
                    state.getMemory(),
                    ExecutionStage.RUNNING,
                    state.getPendingReason(),
                    state.getToolSpecifications()
            );
        }

        ArrayList<ChatMessage> messages = new ArrayList<>(transcriptMemory.getMessages());
        messages.add(new ChatMessage.UserChatMessage("Plan step %d: %s".formatted(step.getOrder(), step.getInstruction())));
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
}
