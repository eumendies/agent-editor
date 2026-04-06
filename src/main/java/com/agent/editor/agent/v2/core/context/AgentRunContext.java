package com.agent.editor.agent.v2.core.context;

import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.ExecutionMemory;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Objects;

/**
 * 单个 agent run 的完整上下文，统一承载请求、运行态和本轮可见工具。
 */
@Data
@NoArgsConstructor
@SuperBuilder
public class AgentRunContext {

    private ExecutionRequest request;
    private int iteration;
    private String currentContent;
    private ExecutionMemory memory = new ChatTranscriptMemory(List.of());
    private ExecutionStage stage = ExecutionStage.RUNNING;
    private String pendingReason;
    private List<ToolSpecification> toolSpecifications = List.of();

    public AgentRunContext(ExecutionRequest request,
                           int iteration,
                           String currentContent,
                           ExecutionMemory memory,
                           ExecutionStage stage,
                           String pendingReason,
                           List<ToolSpecification> toolSpecifications) {
        this.request = request;
        this.iteration = iteration;
        this.currentContent = currentContent;
        setMemory(memory);
        setStage(stage);
        this.pendingReason = pendingReason;
        setToolSpecifications(toolSpecifications);
    }

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

    public void setMemory(ExecutionMemory memory) {
        this.memory = Objects.requireNonNull(memory, "memory must not be null");
    }

    public void setStage(ExecutionStage stage) {
        this.stage = Objects.requireNonNull(stage, "stage must not be null");
    }

    public void setToolSpecifications(List<ToolSpecification> toolSpecifications) {
        this.toolSpecifications = toolSpecifications == null ? List.of() : List.copyOf(toolSpecifications);
    }

    /**
     * 返回当前上下文本身，用于兼容旧调用点里的 {@code state().xxx()} 访问方式。
     *
     * @return 当前上下文实例
     */
    public AgentRunContext state() {
        return this;
    }

    /**
     * 复制当前上下文，并替换执行请求。
     *
     * @param nextRequest 新的执行请求
     * @return 除 request 外其余字段保持不变的新上下文
     */
    public AgentRunContext withRequest(ExecutionRequest nextRequest) {
        return new AgentRunContext(nextRequest, iteration, currentContent, memory, stage, pendingReason, toolSpecifications);
    }

    /**
     * 复制当前上下文，并替换本轮可见工具集合。
     *
     * @param nextToolSpecifications 下一轮暴露给模型的工具规格
     * @return 更新工具列表后的新上下文
     */
    public AgentRunContext withToolSpecifications(List<ToolSpecification> nextToolSpecifications) {
        return new AgentRunContext(request, iteration, currentContent, memory, stage, pendingReason, nextToolSpecifications);
    }

    /**
     * 复制当前上下文，并替换执行阶段。
     *
     * @param nextStage 新阶段
     * @return 更新阶段后的新上下文
     */
    public AgentRunContext withStage(ExecutionStage nextStage) {
        return new AgentRunContext(request, iteration, currentContent, memory, nextStage, pendingReason, toolSpecifications);
    }

    /**
     * 复制当前上下文，并替换当前文档内容快照。
     *
     * @param nextContent 新的文档内容
     * @return 更新正文后的新上下文
     */
    public AgentRunContext withCurrentContent(String nextContent) {
        return new AgentRunContext(request, iteration, nextContent, memory, stage, pendingReason, toolSpecifications);
    }

    /**
     * 复制当前上下文，并替换执行记忆。
     *
     * @param nextMemory 新的执行记忆
     * @return 更新记忆后的新上下文
     */
    public AgentRunContext withMemory(ExecutionMemory nextMemory) {
        return new AgentRunContext(request, iteration, currentContent, nextMemory, stage, pendingReason, toolSpecifications);
    }

    /**
     * 在 request 缺失时安全地返回空任务 ID。
     *
     * @return 当前任务 ID，若未绑定 request 则返回空字符串
     */
    public String getTaskIdOrEmpty() {
        return request == null || request.getTaskId() == null ? "" : request.getTaskId();
    }

    /**
     * 在 request 或 document 缺失时安全地返回空文档 ID。
     *
     * @return 当前文档 ID，若未绑定 document 则返回空字符串
     */
    public String getDocumentIdOrEmpty() {
        return request == null || request.getDocument() == null || request.getDocument().getDocumentId() == null
                ? ""
                : request.getDocument().getDocumentId();
    }

    /**
     * 在 request 或 document 缺失时安全地返回空文档标题。
     *
     * @return 当前文档标题，若未绑定 document 则返回空字符串
     */
    public String getDocumentTitleOrEmpty() {
        return request == null || request.getDocument() == null || request.getDocument().getTitle() == null
                ? ""
                : request.getDocument().getTitle();
    }

    /**
     * 推进到下一轮迭代，并沿用当前记忆。
     *
     * @param nextContent 下一轮开始时看到的文档内容
     * @return iteration 自增后的新上下文
     */
    public AgentRunContext advance(String nextContent) {
        return new AgentRunContext(request, iteration + 1, nextContent, memory, ExecutionStage.RUNNING, pendingReason, toolSpecifications);
    }

    /**
     * 推进到下一轮迭代，并同时替换记忆。
     *
     * @param nextContent 下一轮开始时看到的文档内容
     * @param nextMemory 下一轮要携带的记忆
     * @return iteration 自增后的新上下文
     */
    public AgentRunContext advance(String nextContent, ExecutionMemory nextMemory) {
        return new AgentRunContext(request, iteration + 1, nextContent, nextMemory, ExecutionStage.RUNNING, pendingReason, toolSpecifications);
    }

    /**
     * 将当前上下文标记为完成，不再推进 iteration。
     *
     * @return stage 为 {@link ExecutionStage#COMPLETED} 的新上下文
     */
    public AgentRunContext markCompleted() {
        return new AgentRunContext(request, iteration, currentContent, memory, ExecutionStage.COMPLETED, pendingReason, toolSpecifications);
    }

    /**
     * 追加一条消息到 transcript 型记忆中。
     *
     * @param message 需要追加的消息
     * @return 追加后的新上下文
     */
    public AgentRunContext appendMemory(ChatMessage message) {
        return appendMemory(List.of(message));
    }

    /**
     * 批量追加消息到 transcript 型记忆中。
     *
     * @param messages 需要追加的消息列表
     * @return 追加后的新上下文；当列表为空时直接返回当前实例
     */
    public AgentRunContext appendMemory(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return this;
        }
        if (!(memory instanceof ChatTranscriptMemory transcriptMemory)) {
            throw new IllegalStateException("Execution memory does not support transcript append: "
                    + memory.getClass().getSimpleName());
        }
        List<ChatMessage> merged = new java.util.ArrayList<>(transcriptMemory.getMessages());
        merged.addAll(messages);
        return new AgentRunContext(
                request,
                iteration,
                currentContent,
                new ChatTranscriptMemory(merged, transcriptMemory.getLastObservedTotalTokens()),
                stage,
                pendingReason,
                toolSpecifications
        );
    }
}
