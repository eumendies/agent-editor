package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.ExecutionMemory;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;
import java.util.Objects;

/**
 * 单个 agent run 的完整上下文，统一承载请求、运行态和本轮可见工具。
 */
public record AgentRunContext(
        ExecutionRequest request,
        int iteration,
        String currentContent,
        ExecutionMemory memory,
        ExecutionStage stage,
        String pendingReason,
        List<ToolSpecification> toolSpecifications
) {

    public AgentRunContext(int iteration, String currentContent) {
        this(null, iteration, currentContent, new ChatTranscriptMemory(List.of()), ExecutionStage.RUNNING, null, List.of());
    }

    public AgentRunContext(int iteration,
                           String currentContent,
                           ExecutionMemory memory,
                           ExecutionStage stage,
                           String pendingReason) {
        this(null, iteration, currentContent, memory, stage, pendingReason, List.of());
    }

    public AgentRunContext {
        Objects.requireNonNull(memory, "memory must not be null");
        Objects.requireNonNull(stage, "stage must not be null");
        toolSpecifications = List.copyOf(toolSpecifications);
    }

    // 兼容现有调用点，后续再删掉这一层“上下文里套上下文”的过渡接口。
    public AgentRunContext state() {
        return this;
    }

    public AgentRunContext withRequest(ExecutionRequest nextRequest) {
        return new AgentRunContext(nextRequest, iteration, currentContent, memory, stage, pendingReason, toolSpecifications);
    }

    public AgentRunContext withToolSpecifications(List<ToolSpecification> nextToolSpecifications) {
        return new AgentRunContext(request, iteration, currentContent, memory, stage, pendingReason, nextToolSpecifications);
    }

    public AgentRunContext withStage(ExecutionStage nextStage) {
        return new AgentRunContext(request, iteration, currentContent, memory, nextStage, pendingReason, toolSpecifications);
    }

    public AgentRunContext withCurrentContent(String nextContent) {
        return new AgentRunContext(request, iteration, nextContent, memory, stage, pendingReason, toolSpecifications);
    }

    public AgentRunContext advance(String nextContent) {
        return new AgentRunContext(request, iteration + 1, nextContent, memory, ExecutionStage.RUNNING, pendingReason, toolSpecifications);
    }

    public AgentRunContext advance(String nextContent, ExecutionMemory nextMemory) {
        return new AgentRunContext(request, iteration + 1, nextContent, nextMemory, ExecutionStage.RUNNING, pendingReason, toolSpecifications);
    }

    public AgentRunContext markCompleted() {
        return new AgentRunContext(request, iteration, currentContent, memory, ExecutionStage.COMPLETED, pendingReason, toolSpecifications);
    }

    public AgentRunContext appendMemory(ChatMessage message) {
        return appendMemory(List.of(message));
    }

    public AgentRunContext appendMemory(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return this;
        }
        if (!(memory instanceof ChatTranscriptMemory transcriptMemory)) {
            throw new IllegalStateException("Execution memory does not support transcript append: "
                    + memory.getClass().getSimpleName());
        }
        List<ChatMessage> merged = new java.util.ArrayList<>(transcriptMemory.messages());
        merged.addAll(messages);
        return new AgentRunContext(
                request,
                iteration,
                currentContent,
                new ChatTranscriptMemory(merged),
                stage,
                pendingReason,
                toolSpecifications
        );
    }

    public boolean completed() {
        return stage == ExecutionStage.COMPLETED;
    }

    public List<ToolResult> toolResults() {
        if (!(memory instanceof ChatTranscriptMemory transcriptMemory)) {
            return List.of();
        }
        return transcriptMemory.messages().stream()
                .filter(ChatMessage.ToolExecutionResultChatMessage.class::isInstance)
                .map(ChatMessage.ToolExecutionResultChatMessage.class::cast)
                .map(message -> new ToolResult(message.text()))
                .toList();
    }
}
