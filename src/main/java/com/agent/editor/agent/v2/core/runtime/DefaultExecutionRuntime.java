package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.agent.ToolCall;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import com.agent.editor.agent.v2.core.state.ExecutionState;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
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
        return run(definition, request, new ExecutionState(0, request.document().content()));
    }

    @Override
    public ExecutionResult run(AgentDefinition definition, ExecutionRequest request, ExecutionState initialState) {
        eventPublisher.publish(new ExecutionEvent(EventType.TASK_STARTED, request.taskId(), "execution started"));

        // runtime 维护“本轮文档内容 + 工具结果历史”，每次决策都基于最新状态继续推进。
        ExecutionState state = initialState;
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
                // Complete 表示 agent 明确结束，本轮状态里的 currentContent 就是最终文档内容。
                eventPublisher.publish(new ExecutionEvent(EventType.TASK_COMPLETED, request.taskId(), complete.result()));
                ExecutionState completedState = new ExecutionState(
                        state.iteration(),
                        state.currentContent(),
                        state.memory(),
                        ExecutionStage.COMPLETED,
                        state.pendingReason()
                );
                return new ExecutionResult(complete.result(), state.currentContent(), completedState);
            }

            if (decision instanceof Decision.ToolCalls toolCalls) {
                // ToolCalls 不会直接结束任务，runtime 会先执行工具，再把结果折回下一轮上下文。
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
                // Respond 用在“不再调用工具，但也不需要额外完成语义”的轻量收口场景。
                eventPublisher.publish(new ExecutionEvent(EventType.TASK_COMPLETED, request.taskId(), respond.message()));
                ExecutionState completedState = new ExecutionState(
                        state.iteration(),
                        state.currentContent(),
                        state.memory(),
                        ExecutionStage.COMPLETED,
                        state.pendingReason()
                );
                return new ExecutionResult(respond.message(), state.currentContent(), completedState);
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

            // 工具拿到的是“当前阶段文档内容”，多个 tool call 会在同一轮里顺序叠加修改结果。
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
        // 兼容当前 runtime/reagent 流程，下一阶段再统一切到 transcript-only memory 读写。
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
