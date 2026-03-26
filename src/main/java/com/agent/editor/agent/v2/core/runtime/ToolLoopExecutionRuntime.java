package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.agent.ToolCall;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
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
 * 需要工具循环的单 agent 执行器。
 * 它只负责 decision -> tool execution -> next decision 的循环，不负责多 agent 编排或跨 agent 上下文策略。
 */
public class ToolLoopExecutionRuntime implements ExecutionRuntime {

    private final ToolRegistry toolRegistry;
    private final EventPublisher eventPublisher;
    private final TraceCollector traceCollector;

    public ToolLoopExecutionRuntime(ToolRegistry toolRegistry, EventPublisher eventPublisher, TraceCollector traceCollector) {
        this.toolRegistry = toolRegistry;
        this.eventPublisher = eventPublisher;
        this.traceCollector = traceCollector;
    }

    @Override
    public ExecutionResult run(AgentDefinition definition, ExecutionRequest request) {
        return run(definition, request, new AgentRunContext(0, request.document().content()).withRequest(request));
    }

    @Override
    public ExecutionResult run(AgentDefinition definition, ExecutionRequest request, AgentRunContext initialContext) {
        eventPublisher.publish(new ExecutionEvent(EventType.TASK_STARTED, request.taskId(), "execution started"));

        // runtime 维护“本轮文档内容 + 工具结果历史”，每次决策都基于最新状态继续推进。
        AgentRunContext state = initialContext
                .withRequest(request)
                .withToolSpecifications(toolRegistry.specifications(request.allowedTools()))
                .appendMemory(new ChatMessage.UserChatMessage(request.instruction()));
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
            Decision decision = definition.decide(state);

            if (decision instanceof Decision.Complete complete) {
                // Complete 表示 agent 明确结束，本轮状态里的 currentContent 就是最终文档内容。
                eventPublisher.publish(new ExecutionEvent(EventType.TASK_COMPLETED, request.taskId(), complete.result()));
                AgentRunContext completedState = state
                        .appendMemory(new ChatMessage.AiChatMessage(complete.result()))
                        .markCompleted();
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
                state = state
                        .appendMemory(buildToolInteractionMessages(toolCalls, outcome))
                        .advance(outcome.currentContent());
                continue;
            }

            if (decision instanceof Decision.Respond respond) {
                // Respond 用在“不再调用工具，但也不需要额外完成语义”的轻量收口场景。
                eventPublisher.publish(new ExecutionEvent(EventType.TASK_COMPLETED, request.taskId(), respond.message()));
                AgentRunContext completedState = state
                        .appendMemory(new ChatMessage.AiChatMessage(respond.message()))
                        .markCompleted();
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
        List<ToolExecutionRecord> executions = new ArrayList<>();
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
                            "toolCallId", call.id(),
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
            executions.add(new ToolExecutionRecord(call, result));
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
                            "toolCallId", call.id(),
                            "message", result.message(),
                            "updatedContent", updatedContent
                    )
            ));
            eventPublisher.publish(new ExecutionEvent(EventType.TOOL_SUCCEEDED, taskId, result.message()));
        }
        return new ToolExecutionOutcome(executions, updatedContent);
    }

    private record ToolExecutionOutcome(List<ToolExecutionRecord> executions, String currentContent) {
    }

    private record ToolExecutionRecord(ToolCall call, ToolResult result) {
    }

    private List<ChatMessage> buildToolInteractionMessages(Decision.ToolCalls toolCalls, ToolExecutionOutcome outcome) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage.AiToolCallChatMessage(toolCalls.reasoning(), toolCalls.calls()));
        messages.addAll(outcome.executions().stream()
                .map(execution -> new ChatMessage.ToolExecutionResultChatMessage(
                        execution.call().id(),
                        execution.call().name(),
                        execution.call().arguments(),
                        execution.result().message()
                ))
                .map(ChatMessage.class::cast)
                .toList());
        return messages;
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
