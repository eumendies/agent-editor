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

    public SupervisorContextFactory(MemoryCompressor memoryCompressor) {
        this.memoryCompressor = memoryCompressor;
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

    @Override
    public ModelInvocationContext buildModelInvocationContext(AgentRunContext context) {
        if (!(context instanceof SupervisorContext supervisorContext)) {
            throw new IllegalArgumentException("SupervisorContextFactory requires SupervisorContext");
        }
        return buildRoutingInvocationContext(supervisorContext, supervisorContext.getAvailableWorkers());
    }

    public ModelInvocationContext buildRoutingInvocationContext(SupervisorContext context,
                                                                List<SupervisorContext.WorkerDefinition> candidates) {
        // 历史 worker result 逐条映射为 AI 消息，避免把执行轨迹挤成一大段字符串后丢失轮次边界。
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(routingSystemPrompt()));
        messages.add(UserMessage.from("""
                Task: %s
                Current content:
                %s

                Candidate workers:
                %s
                """.formatted(
                context.getRequest().getInstruction(),
                context.getCurrentContent(),
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
                You are a hybrid supervisor for a document workflow with specialized workers.
                Decide whether the next step should be:
                - researcher: gather evidence from the knowledge base
                - writer: write or revise the document using available context and evidence
                - reviewer: verify both instruction completion and evidence grounding
                - complete: stop when the task is already complete
                Routing policy:
                - If the latest worker result is from writer, the default next step is reviewer.
                - After writer finishes, assign reviewer unless there is a clear reason that more research is required before review.
                - Do not assign writer again immediately after writer unless reviewer feedback or explicit missing evidence makes another writing pass necessary.
                - Only choose complete when the latest content has already been reviewed and no further verification is needed.
                Choose one of the candidate workers or complete the task.
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

    private String emptyIfBlank(String value) {
        return value == null ? "" : value;
    }
}
