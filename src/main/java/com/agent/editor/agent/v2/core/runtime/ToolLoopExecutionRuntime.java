package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.agent.ToolLoopDecision;
import com.agent.editor.agent.v2.core.agent.ToolCall;
import com.agent.editor.agent.v2.core.agent.ToolLoopAgent;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.exception.InCorrectAgentException;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolHandler;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolRegistry;
import com.agent.editor.agent.v2.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 需要工具循环的单 agent 执行器。
 * 它只负责 decision -> tool execution -> next decision 的循环，不负责多 agent 编排或跨 agent 上下文策略。
 */
public class ToolLoopExecutionRuntime implements ExecutionRuntime {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ToolRegistry toolRegistry;
    private final EventPublisher eventPublisher;

    public ToolLoopExecutionRuntime(ToolRegistry toolRegistry, EventPublisher eventPublisher) {
        this.toolRegistry = toolRegistry;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public ExecutionResult run(Agent agent, ExecutionRequest request) throws InCorrectAgentException {
        if (!(agent instanceof ToolLoopAgent)) {
            throw new InCorrectAgentException("ToolLoopExecutionRuntime require ToolLoopAgent type");
        }
        return runInternal((ToolLoopAgent) agent, request, new AgentRunContext(0, request.getDocument().getContent()).withRequest(request));
    }

    @Override
    public ExecutionResult run(Agent agent, ExecutionRequest request, AgentRunContext initialContext) throws InCorrectAgentException {
        if (!(agent instanceof ToolLoopAgent)) {
            throw new InCorrectAgentException("ToolLoopExecutionRuntime require ToolLoopAgent type");
        }
        return runInternal((ToolLoopAgent) agent, request, initialContext);
    }

    public ExecutionResult runInternal(ToolLoopAgent agent, ExecutionRequest request, AgentRunContext initialContext) {
        eventPublisher.publish(new ExecutionEvent(EventType.TASK_STARTED, request.getTaskId(), "execution started"));

        // runtime 维护“本轮文档内容 + 工具结果历史”，每次决策都基于最新状态继续推进。
        AgentRunContext state = initialContext
                .withRequest(request)
                .withToolSpecifications(toolRegistry.specifications(request.getAllowedTools()));
        while (state.getIteration() < request.getMaxIterations() && state.getStage() != com.agent.editor.agent.v2.core.state.ExecutionStage.COMPLETED) {
            eventPublisher.publish(new ExecutionEvent(EventType.ITERATION_STARTED, request.getTaskId(), "iteration " + state.getIteration()));

            // worker 运行时只暴露被允许的工具列表，避免异构 worker 越权调用别的能力。
            ToolLoopDecision toolLoopDecision = agent.decide(state);

            if (toolLoopDecision instanceof ToolLoopDecision.Complete complete) {
                // Complete 表示 agent 明确结束，本轮状态里的 currentContent 就是最终文档内容。
                String finalMessage = normalizeCompletionMessage(complete.getResult());
                eventPublisher.publish(new ExecutionEvent(EventType.TASK_COMPLETED, request.getTaskId(), finalMessage));
                AgentRunContext completedState = state
                        .appendMemory(new ChatMessage.AiChatMessage(finalMessage))
                        .markCompleted();
                return new ExecutionResult(complete.getResult(), finalMessage, state.getCurrentContent(), completedState);
            }

            if (toolLoopDecision instanceof ToolLoopDecision.ToolCalls toolCalls) {
                // ToolCalls 不会直接结束任务，runtime 会先执行工具，再把结果折回下一轮上下文。
                ToolExecutionOutcome outcome = executeTools(
                        request,
                        state.getCurrentContent(),
                        toolCalls.getCalls(),
                        request.getAllowedTools()
                );
                state = state
                        .appendMemory(buildToolInteractionMessages(toolCalls, outcome))
                        .advance(outcome.getCurrentContent());
                continue;
            }

            if (toolLoopDecision instanceof ToolLoopDecision.Respond respond) {
                // Respond 用在“不再调用工具，但也不需要额外完成语义”的轻量收口场景。
                eventPublisher.publish(new ExecutionEvent(EventType.TASK_COMPLETED, request.getTaskId(), respond.getMessage()));
                AgentRunContext completedState = state
                        .appendMemory(new ChatMessage.AiChatMessage(respond.getMessage()))
                        .markCompleted();
                return new ExecutionResult(null, respond.getMessage(), state.getCurrentContent(), completedState);
            }

            throw new IllegalStateException("Unsupported decision type: " + toolLoopDecision.getClass().getSimpleName());
        }

        throw new IllegalStateException("Execution terminated without completion");
    }

    private ToolExecutionOutcome executeTools(ExecutionRequest request,
                                              String currentContent,
                                              List<ToolCall> calls,
                                              List<String> allowedTools) {
        List<ToolExecutionRecord> executions = new ArrayList<>();
        String updatedContent = currentContent;
        for (ToolCall call : calls) {
            eventPublisher.publish(new ExecutionEvent(EventType.TOOL_CALLED, request.getTaskId(), call.getName()));

            ToolHandler handler = toolRegistry.get(call.getName());
            // 这里同时做“是否存在”和“是否允许”两层校验，错误统一收敛成不可用工具。
            if (handler == null || !toolRegistry.isAllowed(call.getName(), allowedTools)) {
                eventPublisher.publish(new ExecutionEvent(EventType.TOOL_FAILED, request.getTaskId(), call.getName()));
                throw new IllegalStateException("No tool handler registered for " + call.getName());
            }

            // 工具拿到的是“当前阶段文档内容”，多个 tool call 会在同一轮里顺序叠加修改结果。
            ToolResult result = handler.execute(
                    new ToolInvocation(call.getName(), call.getArguments()),
                    new ToolContext(
                            request.getTaskId(),
                            request.getDocument() == null ? null : request.getDocument().getDocumentId(),
                            request.getDocument() == null ? null : request.getDocument().getTitle(),
                            updatedContent
                    )
            );
            executions.add(new ToolExecutionRecord(call, result));
            if (result.getUpdatedContent() != null) {
                updatedContent = result.getUpdatedContent();
            }
            eventPublisher.publish(new ExecutionEvent(EventType.TOOL_SUCCEEDED, request.getTaskId(), result.getMessage()));
        }
        return new ToolExecutionOutcome(executions, updatedContent);
    }

    @Getter
    @AllArgsConstructor
    private static final class ToolExecutionOutcome {

        private final List<ToolExecutionRecord> executions;
        private final String currentContent;
    }

    @Getter
    @AllArgsConstructor
    private static final class ToolExecutionRecord {

        private final ToolCall call;
        private final ToolResult result;
    }

    private List<ChatMessage> buildToolInteractionMessages(ToolLoopDecision.ToolCalls toolCalls, ToolExecutionOutcome outcome) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage.AiToolCallChatMessage(toolCalls.getReasoning(), toolCalls.getCalls()));
        messages.addAll(outcome.getExecutions().stream()
                .map(execution -> new ChatMessage.ToolExecutionResultChatMessage(
                        execution.getCall().getId(),
                        execution.getCall().getName(),
                        execution.getCall().getArguments(),
                        execution.getResult().getMessage()
                ))
                .map(ChatMessage.class::cast)
                .toList());
        return messages;
    }

    private String normalizeCompletionMessage(Object result) {
        if (result == null) {
            return "";
        }
        if (result instanceof String text) {
            return text;
        }
        try {
            // 结构化完成结果需要落成稳定 JSON，供 memory/supervisor 后续继续消费，而不是依赖对象 toString。
            return OBJECT_MAPPER.writeValueAsString(result);
        } catch (Exception ignored) {
            return String.valueOf(result);
        }
    }

}
