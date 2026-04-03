package com.agent.editor.agent.v2.reflexion;

import com.agent.editor.agent.v2.core.context.AgentContextFactory;
import com.agent.editor.agent.v2.core.context.ModelInvocationContext;
import com.agent.editor.agent.v2.core.context.CompressContextMemory;
import com.agent.editor.agent.v2.core.context.MemoryCompressionCapableContextFactory;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.ExecutionMemory;
import com.agent.editor.agent.v2.core.memory.MemoryCompressor;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.mapper.ExecutionMemoryChatMessageMapper;
import com.agent.editor.agent.v2.task.TaskRequest;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;

import java.util.ArrayList;
import java.util.List;

public class ReflexionCriticContextFactory implements AgentContextFactory, MemoryCompressionCapableContextFactory {

    private static final JsonSchema REFLEXION_CRITIQUE_SCHEMA = JsonSchema.builder()
            .name("reflexion_critique")
            .rootElement(JsonObjectSchema.builder()
                    .addProperty("verdict", JsonEnumSchema.builder()
                            .description("Final routing decision for the reflexion loop")
                            .enumValues("PASS", "REVISE")
                            .build())
                    .addStringProperty("feedback", "Concise actionable critique for the actor")
                    .addStringProperty("reasoning", "Short explanation for the verdict")
                    .required("verdict", "feedback", "reasoning")
                    .additionalProperties(false)
                    .build())
            .build();

    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;
    private final MemoryCompressor memoryCompressor;

    public ReflexionCriticContextFactory(MemoryCompressor memoryCompressor) {
        this(new ExecutionMemoryChatMessageMapper(), memoryCompressor);
    }

    public ReflexionCriticContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
                                         MemoryCompressor memoryCompressor) {
        this.memoryChatMessageMapper = memoryChatMessageMapper;
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

    /**
     * 为每轮 critic 评审创建 fresh 上下文，避免继承上一轮 critic 的推理轨迹。
     *
     * @param request 原始任务请求
     * @param actorState 当前 actor 状态
     * @param actorSummary actor 对本轮结果的摘要
     * @return 仅包含评审所需关键信息的新上下文
     */
    @CompressContextMemory
    public AgentRunContext prepareReviewContext(TaskRequest request, AgentRunContext actorState, String actorSummary) {
        return new AgentRunContext(
                actorState.getRequest(),
                0,
                actorState.getCurrentContent(),
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage(request.getInstruction()),
                        new ChatMessage.AiChatMessage("""
                                Current Content:
                                %s
                                Actor Summary:
                                %s
                                """.formatted(actorState.getCurrentContent(), actorSummary))
                )),
                ExecutionStage.RUNNING,
                actorState.getPendingReason(),
                actorState.getToolSpecifications()
        );
    }

    /**
     * 构造 critic 的分析阶段模型上下文，允许继续调用工具搜证。
     *
     * @param context critic 当前状态
     * @return 带分析系统提示词和工具规格的模型调用上下文
     */
    @Override
    public ModelInvocationContext buildModelInvocationContext(AgentRunContext context) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(analysisSystemPrompt()));
        messages.addAll(memoryChatMessageMapper.toChatMessages(context.getMemory()));
        return new ModelInvocationContext(messages, context.getToolSpecifications(), null);
    }

    /**
     * 构造 critic 的收口阶段模型上下文，强制输出结构化 verdict。
     *
     * @param context critic 当前状态
     * @param analysisText 前一阶段产出的分析文本
     * @return 不允许再调用工具、只允许输出 JSON verdict 的上下文
     */
    public ModelInvocationContext buildFinalizationInvocationContext(AgentRunContext context, String analysisText) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(finalizationSystemPrompt()));
        messages.addAll(memoryChatMessageMapper.toChatMessages(context.getMemory()));
        messages.add(UserMessage.from("""
                Current document:
                %s

                Draft critique analysis:
                %s
                """.formatted(
                context.getCurrentContent(),
                analysisText == null ? "" : analysisText
        )));
        return new ModelInvocationContext(
                messages,
                List.of(),
                ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(REFLEXION_CRITIQUE_SCHEMA)
                        .build()
        );
    }

    private ExecutionMemory appendUserMessage(ExecutionMemory memory, String instruction) {
        if (!(memory instanceof ChatTranscriptMemory transcriptMemory)) {
            return memory;
        }
        ArrayList<ChatMessage> messages = new ArrayList<>(transcriptMemory.getMessages());
        messages.add(new ChatMessage.UserChatMessage(instruction));
        return new ChatTranscriptMemory(messages, transcriptMemory.getLastObservedTotalTokens());
    }

    @Override
    public MemoryCompressor memoryCompressor() {
        return memoryCompressor;
    }

    private String analysisSystemPrompt() {
        return """
                You are a critic for a document editing reflexion workflow.
                Review the current draft against the instruction.
                Use tools whenever more evidence is needed.
                You may call tools multiple times until you have enough evidence.
                When you are ready to finish, return critique JSON with:
                - verdict: PASS or REVISE
                - feedback: concise actionable feedback
                - reasoning: concise explanation
                """;
    }

    private String finalizationSystemPrompt() {
        return """
                You are a critic for a document editing reflexion workflow.
                Finalize the critique based on the gathered evidence.
                Do not call any tools.
                Return only strict JSON with:
                - verdict: PASS or REVISE
                - feedback: concise actionable feedback
                - reasoning: concise explanation
                """;
    }
}
