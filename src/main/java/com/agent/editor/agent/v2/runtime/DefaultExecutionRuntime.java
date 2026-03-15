package com.agent.editor.agent.v2.runtime;

import com.agent.editor.agent.v2.definition.AgentDefinition;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.agent.ToolCall;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import com.agent.editor.agent.v2.core.state.ExecutionState;
import com.agent.editor.agent.v2.trace.TraceCategory;
import com.agent.editor.agent.v2.trace.TraceCollector;
import com.agent.editor.agent.v2.trace.TraceRecord;
import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolHandler;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolRegistry;
import com.agent.editor.agent.v2.tool.ToolResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 通用单 agent runtime。
 * 它只负责 decision -> tool execution -> next decision 的循环，不关心上层是 ReAct、Planning 还是 Supervisor worker。
 */
public class DefaultExecutionRuntime implements ExecutionRuntime {

    private final ToolRegistry toolRegistry;
    private final EventPublisher eventPublisher;
    private final TraceCollector traceCollector;

    public DefaultExecutionRuntime(ToolRegistry toolRegistry, EventPublisher eventPublisher, TraceCollector traceCollector) {
        this.toolRegistry = toolRegistry;
        this.eventPublisher = eventPublisher;
        this.traceCollector = traceCollector;
    }

    @Override
    public ExecutionResult run(AgentDefinition definition, ExecutionRequest request) {
        eventPublisher.publish(new ExecutionEvent(EventType.TASK_STARTED, request.taskId(), "execution started"));

        ExecutionState state = new ExecutionState(0, false, request.document().content());
        while (state.iteration() < request.maxIterations() && !state.completed()) {
            eventPublisher.publish(new ExecutionEvent(EventType.ITERATION_STARTED, request.taskId(), "iteration " + state.iteration()));
            traceCollector.collect(traceRecord(
                    request,
                    state.iteration(),
                    TraceCategory.STATE_SNAPSHOT,
                    "runtime.iteration.started",
                    Map.of(
                            "currentContent", state.currentContent(),
                            "toolResults", state.toolResults().stream().map(ToolResult::message).toList(),
                            "maxIterations", request.maxIterations()
                    )
            ));

            // worker 运行时只暴露被允许的工具列表，避免异构 worker 越权调用别的能力。
            ExecutionContext context = new ExecutionContext(request, state, toolRegistry.specifications(request.allowedTools()));
            Decision decision = definition.decide(context);

            if (decision instanceof Decision.Complete complete) {
                eventPublisher.publish(new ExecutionEvent(EventType.TASK_COMPLETED, request.taskId(), complete.result()));
                return new ExecutionResult(complete.result(), state.currentContent());
            }

            if (decision instanceof Decision.ToolCalls toolCalls) {
                ToolExecutionOutcome outcome = executeTools(
                        request,
                        state.iteration(),
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

    private ToolExecutionOutcome executeTools(ExecutionRequest request,
                                             int iteration,
                                             String taskId,
                                             String currentContent,
                                             List<ToolCall> calls,
                                             List<String> allowedTools) {
        List<ToolResult> results = new ArrayList<>();
        String updatedContent = currentContent;
        for (ToolCall call : calls) {
            eventPublisher.publish(new ExecutionEvent(EventType.TOOL_CALLED, taskId, call.name()));
            traceCollector.collect(traceRecord(
                    request,
                    iteration,
                    TraceCategory.TOOL_INVOCATION,
                    "runtime.tool.invocation",
                    Map.of(
                            "toolName", call.name(),
                            "arguments", call.arguments(),
                            "currentContent", updatedContent
                    )
            ));

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
            traceCollector.collect(traceRecord(
                    request,
                    iteration,
                    TraceCategory.TOOL_RESULT,
                    "runtime.tool.result",
                    Map.of(
                            "toolName", call.name(),
                            "message", result.message(),
                            "updatedContent", updatedContent
                    )
            ));
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

    private TraceRecord traceRecord(ExecutionRequest request,
                                    int iteration,
                                    TraceCategory category,
                                    String stage,
                                    Map<String, Object> payload) {
        return new TraceRecord(
                UUID.randomUUID().toString(),
                request.taskId(),
                Instant.now(),
                category,
                stage,
                request.agentType(),
                request.workerId(),
                iteration,
                payload
        );
    }
}
