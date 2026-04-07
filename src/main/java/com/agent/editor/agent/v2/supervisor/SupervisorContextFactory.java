package com.agent.editor.agent.v2.supervisor;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.context.AgentContextFactory;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.context.CompressContextMemory;
import com.agent.editor.agent.v2.core.context.MemoryCompressionCapableContextFactory;
import com.agent.editor.agent.v2.core.context.ModelInvocationContext;
import com.agent.editor.agent.v2.core.context.SupervisorContext;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.ExecutionMemory;
import com.agent.editor.agent.v2.core.memory.MemoryCompressor;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.service.StructuredDocumentService;
import com.agent.editor.utils.rag.markdown.MarkdownSectionTreeBuilder;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * supervisor 工作流的上下文装配器。
 * 负责在 supervisor 视角和 worker 视角之间切换上下文形态，并控制跨轮记忆的压缩方式。
 */
public class SupervisorContextFactory implements AgentContextFactory, MemoryCompressionCapableContextFactory {

    private static final String ASSIGN_WORKER_ACTION = "assign_worker";
    private static final String COMPLETE_ACTION = "complete";

    private static final JsonSchema SUPERVISOR_ROUTING_SCHEMA = JsonSchema.builder()
            .name("supervisor_routing")
            .rootElement(JsonObjectSchema.builder()
                    .addProperty("action", JsonEnumSchema.builder()
                            .description("Next routing action")
                            .enumValues(ASSIGN_WORKER_ACTION, COMPLETE_ACTION)
                            .build())
                    .addStringProperty("workerId", "Candidate worker id when assigning")
                    .addStringProperty("instruction", "Instruction for the selected worker when assigning")
                    .addStringProperty("summary", "Final completion summary when action is complete")
                    .addStringProperty("finalContent", "Final content when action is complete")
                    .addStringProperty("reasoning", "Concise explanation for the routing choice")
                    .required("action", "reasoning")
                    .additionalProperties(false)
                    .build())
            .build();

    private final MemoryCompressor memoryCompressor;
    private final StructuredDocumentService structuredDocumentService;

    public SupervisorContextFactory(MemoryCompressor memoryCompressor) {
        this(memoryCompressor, new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 4_000, 1_200));
    }

    public SupervisorContextFactory(MemoryCompressor memoryCompressor,
                                    StructuredDocumentService structuredDocumentService) {
        this.memoryCompressor = memoryCompressor;
        this.structuredDocumentService = structuredDocumentService;
    }

    @Override
    @CompressContextMemory
    public AgentRunContext prepareInitialContext(TaskRequest request) {
        return new AgentRunContext(
                null,
                0,
                request.getDocument().getContent(),
                appendUserMessage(request.getMemory(), request.getInstruction()),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );
    }

    /**
     * 基于当前会话状态重建一份 supervisor 视角的上下文快照。
     *
     * @param request 原始任务请求
     * @param conversationState 当前 supervisor 侧会话状态
     * @param workerResults 累积的 worker 执行结果
     * @param availableWorkers 当前允许调度的 worker 列表
     * @return 单轮 supervisor 决策使用的上下文
     */
    public SupervisorContext buildSupervisorContext(TaskRequest request,
                                                    AgentRunContext conversationState,
                                                    List<SupervisorContext.WorkerResult> workerResults,
                                                    List<SupervisorContext.WorkerDefinition> availableWorkers) {
        // 每轮都基于最新 conversationState 重建 SupervisorContext，确保路由决策只依赖当前快照而不是旧引用。
        return SupervisorContext.builder()
                .request(new ExecutionRequest(
                        request.getTaskId(),
                        request.getSessionId(),
                        AgentType.SUPERVISOR,
                        new DocumentSnapshot(
                                request.getDocument().getDocumentId(),
                                request.getDocument().getTitle(),
                                conversationState.getCurrentContent()
                        ),
                        request.getInstruction(),
                        request.getMaxIterations()
                ))
                .iteration(conversationState.getIteration())
                .currentContent(conversationState.getCurrentContent())
                .memory(conversationState.getMemory())
                .stage(ExecutionStage.RUNNING)
                .pendingReason(conversationState.getPendingReason())
                .toolSpecifications(List.of())
                .availableWorkers(List.copyOf(availableWorkers))
                .workerResults(List.copyOf(workerResults))
                .build();
    }

    /**
     * 将 supervisor 会话状态转换为 worker 可执行的上下文，并把本轮分派指令追加为最新 user message。
     *
     * @param conversationState 当前 supervisor 会话状态
     * @param currentContent 交给 worker 处理的最新文档内容
     * @param instruction 发给 worker 的指令
     * @return worker 执行起点上下文
     */
    public AgentRunContext buildWorkerExecutionContext(AgentRunContext conversationState,
                                                       String currentContent,
                                                       String instruction) {
        return new AgentRunContext(
                null,
                conversationState.getIteration(),
                currentContent,
                // worker 执行前只保留摘要型记忆，避免工具调用明细污染下一轮 prompt。
                // worker 当前指令必须作为 transcript 末尾的 user turn 落入记忆，后续 prompt 才能保持严格时序。
                appendUserMessage(
                        keepSummaryMemory(memoryCompressor.compressOrOriginal(conversationState.getMemory())),
                        instruction
                ),
                ExecutionStage.RUNNING,
                conversationState.getPendingReason(),
                List.of()
        );
    }

    /**
     * 将单个 worker 的最终结果压缩成摘要回写到 supervisor 会话记忆中。
     *
     * @param conversationState 当前 supervisor 会话状态
     * @param workerId 刚完成执行的 worker 标识
     * @param result worker 最终执行结果
     * @return 供下一轮 supervisor 继续使用的新上下文
     */
    @CompressContextMemory
    public AgentRunContext summarizeWorkerResult(AgentRunContext conversationState,
                                                 String workerId,
                                                 ExecutionResult<?> result) {
        // supervisor 只需要知道“谁做了什么、产出了什么”，不需要把 worker 的内部推理全文重新塞回记忆。
        return conversationState.appendMemory(new ChatMessage.AiChatMessage("""
                Previous worker result:
                workerId: %s
                summary: %s
                """.formatted(workerId, normalizeWorkerSummary(result))))
                .withCurrentContent(result.getFinalContent())
                .withStage(ExecutionStage.RUNNING);
    }

    /**
     * 将 supervisor 上下文转换为路由模型调用上下文。
     *
     * @param context 当前 supervisor 状态
     * @return 带结构化输出约束的模型调用上下文
     */
    @Override
    public ModelInvocationContext buildModelInvocationContext(AgentRunContext context) {
        if (!(context instanceof SupervisorContext supervisorContext)) {
            throw new IllegalArgumentException("SupervisorContextFactory requires SupervisorContext");
        }
        return buildRoutingInvocationContext(supervisorContext, supervisorContext.getAvailableWorkers());
    }

    /**
     * 按给定候选 worker 列表构造一次路由模型调用。
     *
     * @param context 当前 supervisor 状态
     * @param candidates 本轮允许选择的 worker 集合
     * @return 只允许输出路由 JSON 的模型调用上下文
     */
    public ModelInvocationContext buildRoutingInvocationContext(SupervisorContext context,
                                                                List<SupervisorContext.WorkerDefinition> candidates) {
        // 历史 worker result 逐条映射为 AI 消息，避免把执行轨迹挤成一大段字符串后丢失轮次边界。
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(routingSystemPrompt()));
        // 候选 worker 属于“本轮请求态信息”，可能随路由裁剪、扩展新 worker 而变化。
        // 因此它必须放在 user message 里动态渲染，避免 system prompt 和注册表出现双份定义后逐渐漂移。
        messages.add(UserMessage.from("""
                ## Task
                %s

                ## Document Structure JSON
                %s

                ## Candidate Workers
                %s
                """.formatted(
                context.getRequest().getInstruction(),
                structureJson(context),
                renderCandidates(candidates)
        )));
        context.getWorkerResults().stream()
                .map(this::toRoutingHistoryMessage)
                .forEach(messages::add);
        return new ModelInvocationContext(
                messages,
                List.of(),
                ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(SUPERVISOR_ROUTING_SCHEMA)
                        .build()
        );
    }

    /**
     * 在模型路由不可用时，为指定 worker 生成可直接执行的兜底指令。
     *
     * @param worker 目标 worker 定义
     * @param context 当前 supervisor 状态
     * @return 包含角色描述和原始任务的兜底执行指令
     */
    public String buildFallbackInstruction(SupervisorContext.WorkerDefinition worker, SupervisorContext context) {
        return worker.getRole() + ": " + worker.getDescription() + "\nTask: " + context.getRequest().getInstruction();
    }

    private ExecutionMemory keepSummaryMemory(ExecutionMemory memory) {
        if (!(memory instanceof ChatTranscriptMemory transcriptMemory)) {
            return memory;
        }
        // supervisor/worker 的跨轮记忆以结果摘要为主，工具调用与结果明细在这里被裁掉，防止上下文指数膨胀。
        return new ChatTranscriptMemory(
                transcriptMemory.getMessages().stream()
                        .filter(message -> !(message instanceof ChatMessage.AiToolCallChatMessage))
                        .filter(message -> !(message instanceof ChatMessage.ToolExecutionResultChatMessage))
                        .toList(),
                transcriptMemory.getLastObservedTotalTokens()
        );
    }

    private ExecutionMemory appendUserMessage(ExecutionMemory memory, String instruction) {
        if (!(memory instanceof ChatTranscriptMemory transcriptMemory)) {
            return memory;
        }
        List<ChatMessage> messages = new ArrayList<>(transcriptMemory.getMessages());
        messages.add(new ChatMessage.UserChatMessage(instruction));
        return new ChatTranscriptMemory(messages, transcriptMemory.getLastObservedTotalTokens());
    }

    private AgentRunContext compressContextMemory(AgentRunContext context) {
        return context.withMemory(memoryCompressor.compressOrOriginal(context.getMemory()));
    }

    @Override
    public MemoryCompressor memoryCompressor() {
        return memoryCompressor;
    }

    /**
     * 将候选 worker 列表渲染成供模型阅读的文本摘要。
     *
     * @param candidates 候选 worker 列表
     * @return 每个 worker 一行的文本表示；为空时返回占位文案
     */
    public String renderCandidates(List<SupervisorContext.WorkerDefinition> candidates) {
        if (candidates.isEmpty()) {
            return "No candidate workers";
        }
        return candidates.stream()
                .map(worker -> worker.getWorkerId()
                        + " | role=" + worker.getRole()
                        + " | description=" + worker.getDescription()
                        + " | capabilities=" + String.join(", ", worker.getCapabilities()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("No candidate workers");
    }

    private String normalizeWorkerSummary(ExecutionResult<?> result) {
        if (result.getFinalMessage() != null && !result.getFinalMessage().isBlank()) {
            return result.getFinalMessage();
        }
        if (result.getFinalContent() != null && !result.getFinalContent().isBlank()) {
            return result.getFinalContent();
        }
        return "worker completed";
    }

    private AiMessage toRoutingHistoryMessage(SupervisorContext.WorkerResult workerResult) {
        String updatedContentSection = SupervisorWorkerIds.WRITER.equals(workerResult.getWorkerId())
                ? "\nupdatedContent: %s".formatted(emptyIfBlank(workerResult.getUpdatedContent()))
                : "";
        return AiMessage.from("""
                Previous worker result:
                workerId: %s
                status: %s
                summary: %s%s
                """.formatted(
                workerResult.getWorkerId(),
                workerResult.getStatus(),
                emptyIfBlank(workerResult.getSummary()),
                updatedContentSection
        ));
    }

    private String routingSystemPrompt() {
        return """
                ## Role
                You are a hybrid supervisor for a document workflow with specialized workers.

                ## Available Actions
                Decide whether the next step should be:
                - assign_worker: choose exactly one worker from the candidate worker list in the user message
                - complete: stop when the task is already complete

                ## Routing Policy
                - If the latest worker result is from writer, the default next step is reviewer.
                - After writer finishes, assign reviewer unless there is a clear reason that more research is required before review.
                - Do not assign writer again immediately after writer unless reviewer feedback or explicit missing evidence makes another writing pass necessary.
                - Only choose complete when the latest content has already been reviewed and no further verification is needed.
                - Choose one of the candidate workers or complete the task.

                ## Output Rules
                Return only JSON using:
                - action: assign_worker or complete
                - workerId: candidate worker id when assigning
                - instruction: required when assigning
                - summary: required when completing
                - finalContent: required when completing
                - reasoning: concise explanation
                The workerId must be one of the candidate workers listed in the user message.
                """;
    }

    private String structureJson(SupervisorContext context) {
        if (context.getRequest() == null || context.getRequest().getDocument() == null) {
            return "(no document)";
        }
        return structuredDocumentService.renderStructureJson(
                context.getRequest().getDocument().getTitle(),
                context.getCurrentContent()
        );
    }

    private String emptyIfBlank(String value) {
        return value == null ? "" : value;
    }
}
