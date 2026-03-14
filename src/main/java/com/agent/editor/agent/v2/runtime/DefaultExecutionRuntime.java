package com.agent.editor.agent.v2.runtime;

import com.agent.editor.agent.v2.definition.AgentDefinition;
import com.agent.editor.agent.v2.definition.Decision;
import com.agent.editor.agent.v2.definition.ToolCall;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import com.agent.editor.agent.v2.state.ExecutionState;
import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolHandler;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolRegistry;
import com.agent.editor.agent.v2.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用单 agent runtime。
 * 它只负责 decision -> tool execution -> next decision 的循环，不关心上层是 ReAct、Planning 还是 Supervisor worker。
 */
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

        ExecutionState state = new ExecutionState(0, false, request.document().content());
        while (state.iteration() < request.maxIterations() && !state.completed()) {
            eventPublisher.publish(new ExecutionEvent(EventType.ITERATION_STARTED, request.taskId(), "iteration " + state.iteration()));

            // worker 运行时只暴露被允许的工具列表，避免异构 worker 越权调用别的能力。
            ExecutionContext context = new ExecutionContext(request, state, toolRegistry.specifications(request.allowedTools()));
            Decision decision = definition.decide(context);

            if (decision instanceof Decision.Complete complete) {
                eventPublisher.publish(new ExecutionEvent(EventType.TASK_COMPLETED, request.taskId(), complete.result()));
                return new ExecutionResult(complete.result(), state.currentContent());
            }

            if (decision instanceof Decision.ToolCalls toolCalls) {
                ToolExecutionOutcome outcome = executeTools(
                        request.taskId(),
                        state.currentContent(),
                        toolCalls.calls(),
                        request.allowedTools()
                );
                state = new ExecutionState(
                        state.iteration() + 1,
                        false,
                        outcome.currentContent(),
                        mergeToolResults(state.toolResults(), outcome.toolResults())
                );
                continue;
            }

            if (decision instanceof Decision.Respond respond) {
                eventPublisher.publish(new ExecutionEvent(EventType.TASK_COMPLETED, request.taskId(), respond.message()));
                return new ExecutionResult(respond.message(), state.currentContent());
            }

            throw new IllegalStateException("Unsupported decision type: " + decision.getClass().getSimpleName());
        }

        throw new IllegalStateException("Execution terminated without completion");
    }

    private ToolExecutionOutcome executeTools(String taskId,
                                             String currentContent,
                                             List<ToolCall> calls,
                                             List<String> allowedTools) {
        List<ToolResult> results = new ArrayList<>();
        String updatedContent = currentContent;
        for (ToolCall call : calls) {
            eventPublisher.publish(new ExecutionEvent(EventType.TOOL_CALLED, taskId, call.name()));

            ToolHandler handler = toolRegistry.get(call.name());
            // 这里同时做“是否存在”和“是否允许”两层校验，错误统一收敛成不可用工具。
            if (handler == null || !toolRegistry.isAllowed(call.name(), allowedTools)) {
                eventPublisher.publish(new ExecutionEvent(EventType.TOOL_FAILED, taskId, call.name()));
                throw new IllegalStateException("No tool handler registered for " + call.name());
            }

            ToolResult result = handler.execute(new ToolInvocation(call.name(), call.arguments()), new ToolContext(taskId, updatedContent));
            results.add(result);
            if (result.updatedContent() != null) {
                updatedContent = result.updatedContent();
            }
            eventPublisher.publish(new ExecutionEvent(EventType.TOOL_SUCCEEDED, taskId, result.message()));
        }
        return new ToolExecutionOutcome(results, updatedContent);
    }

    private record ToolExecutionOutcome(List<ToolResult> toolResults, String currentContent) {
    }

    private List<ToolResult> mergeToolResults(List<ToolResult> existingResults, List<ToolResult> newResults) {
        List<ToolResult> merged = new ArrayList<>(existingResults);
        merged.addAll(newResults);
        return merged;
    }
}
