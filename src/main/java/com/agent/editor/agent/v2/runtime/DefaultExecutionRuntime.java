package com.agent.editor.agent.v2.runtime;

import com.agent.editor.agent.v2.definition.AgentDefinition;
import com.agent.editor.agent.v2.definition.Decision;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import com.agent.editor.agent.v2.state.ExecutionState;
import com.agent.editor.agent.v2.tool.ToolRegistry;

public class DefaultExecutionRuntime implements ExecutionRuntime {

    private final ToolRegistry toolRegistry;
    private final EventPublisher eventPublisher;

    public DefaultExecutionRuntime(ToolRegistry toolRegistry, EventPublisher eventPublisher) {
        this.toolRegistry = toolRegistry;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public ExecutionResult run(AgentDefinition definition, ExecutionRequest request) {
        eventPublisher.publish(new ExecutionEvent(EventType.TASK_STARTED, request.taskId(), "execution started"));

        ExecutionContext context = new ExecutionContext(request, new ExecutionState(0, false));
        Decision decision = definition.decide(context);

        if (decision instanceof Decision.Complete complete) {
            eventPublisher.publish(new ExecutionEvent(EventType.TASK_COMPLETED, request.taskId(), complete.result()));
            return new ExecutionResult(complete.result());
        }

        throw new IllegalStateException("Unsupported decision type: " + decision.getClass().getSimpleName());
    }
}
