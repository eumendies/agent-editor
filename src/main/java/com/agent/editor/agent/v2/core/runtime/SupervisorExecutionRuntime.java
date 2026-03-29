package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.SupervisorAgent;
import com.agent.editor.agent.v2.core.agent.SupervisorDecision;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.context.SupervisorContext;
import com.agent.editor.agent.v2.core.exception.InCorrectAgentException;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;

import java.util.List;

public class SupervisorExecutionRuntime implements ExecutionRuntime {

    private final EventPublisher eventPublisher;

    public SupervisorExecutionRuntime(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public ExecutionResult<SupervisorDecision> run(Agent agent, ExecutionRequest request) {
        if (!(agent instanceof SupervisorAgent supervisorAgent)) {
            throw new InCorrectAgentException("SupervisorExecutionRuntime require SupervisorAgent type");
        }
        SupervisorContext initialContext = SupervisorContext.builder()
                .request(request)
                .iteration(0)
                .currentContent(request.getDocument().getContent())
                .memory(new com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory(List.of()))
                .stage(com.agent.editor.agent.v2.core.state.ExecutionStage.RUNNING)
                .pendingReason(null)
                .toolSpecifications(List.of())
                .availableWorkers(List.of())
                .workerResults(List.of())
                .build();
        return runInternal(supervisorAgent, request, initialContext);
    }

    @Override
    public ExecutionResult<SupervisorDecision> run(Agent agent, ExecutionRequest request, AgentRunContext initialContext) {
        if (!(agent instanceof SupervisorAgent supervisorAgent)) {
            throw new InCorrectAgentException("SupervisorExecutionRuntime require SupervisorAgent type");
        }
        if (!(initialContext instanceof SupervisorContext supervisorContext)) {
            throw new IllegalArgumentException("SupervisorExecutionRuntime require SupervisorContext initial state");
        }
        return runInternal(supervisorAgent, request, supervisorContext);
    }

    private ExecutionResult<SupervisorDecision> runInternal(SupervisorAgent agent,
                                                            ExecutionRequest request,
                                                            SupervisorContext initialContext) {
        eventPublisher.publish(new ExecutionEvent(EventType.TASK_STARTED, request.getTaskId(), "supervisor execution started"));

        SupervisorContext state = SupervisorContext.builder()
                .request(request)
                .iteration(initialContext.getIteration())
                .currentContent(initialContext.getCurrentContent())
                .memory(initialContext.getMemory())
                .stage(initialContext.getStage())
                .pendingReason(initialContext.getPendingReason())
                .toolSpecifications(initialContext.getToolSpecifications())
                .availableWorkers(initialContext.getAvailableWorkers())
                .workerResults(initialContext.getWorkerResults())
                .build();
        SupervisorDecision decision = agent.decide(state);
        String finalMessage = buildFinalMessage(decision);
        String finalContent = extractFinalContent(decision, state);

        eventPublisher.publish(new ExecutionEvent(EventType.TASK_COMPLETED, request.getTaskId(), finalMessage));

        AgentRunContext finalState = state
                .appendMemory(new ChatMessage.AiChatMessage(finalMessage))
                .withCurrentContent(finalContent)
                .markCompleted();
        return new ExecutionResult<>(decision, finalMessage, finalContent, finalState);
    }

    private String buildFinalMessage(SupervisorDecision decision) {
        if (decision instanceof SupervisorDecision.AssignWorker assignWorker) {
            return "assign worker: " + assignWorker.getWorkerId();
        }
        if (decision instanceof SupervisorDecision.Complete complete) {
            return "complete: " + complete.getSummary();
        }
        throw new IllegalStateException("Unsupported decision type: " + decision.getClass().getSimpleName());
    }

    private String extractFinalContent(SupervisorDecision decision, SupervisorContext state) {
        if (decision instanceof SupervisorDecision.AssignWorker) {
            return state.getCurrentContent();
        }
        if (decision instanceof SupervisorDecision.Complete complete) {
            return complete.getFinalContent();
        }
        throw new IllegalStateException("Unsupported decision type: " + decision.getClass().getSimpleName());
    }
}
