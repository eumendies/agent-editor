package com.agent.editor.agent.reflexion;

import com.agent.editor.agent.core.context.AgentContextFactory;
import com.agent.editor.agent.core.context.ModelInvocationContext;
import com.agent.editor.agent.core.context.CompressContextMemory;
import com.agent.editor.agent.core.context.MemoryCompressionCapableContextFactory;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.core.memory.ExecutionMemory;
import com.agent.editor.agent.core.memory.MemoryCompressor;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.state.ExecutionStage;
import com.agent.editor.agent.mapper.ExecutionMemoryChatMessageMapper;
import com.agent.editor.agent.task.TaskRequest;
import com.agent.editor.service.StructuredDocumentService;
import com.agent.editor.agent.tool.document.DocumentToolMode;
import com.agent.editor.agent.tool.document.DocumentToolNames;
import com.agent.editor.agent.tool.memory.MemoryToolNames;
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
    private final StructuredDocumentService structuredDocumentService;

    public ReflexionCriticContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
                                         MemoryCompressor memoryCompressor,
                                         StructuredDocumentService structuredDocumentService) {
        this.memoryChatMessageMapper = memoryChatMessageMapper;
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
     * 为每轮 critic 评审创建 fresh 上下文，避免继承上一轮 critic 的推理轨迹。
     *
     * @param request 原始任务请求
     * @param actorState 当前 actor 状态
     * @param actorSummary actor 对本轮结果的摘要
     * @return 仅包含评审所需关键信息的新上下文
     */
    @CompressContextMemory
    public AgentRunContext prepareReviewContext(TaskRequest request, AgentRunContext actorState, String actorSummary) {
        DocumentToolMode documentToolMode = documentToolMode(actorState);
        return new AgentRunContext(
                actorState.getRequest(),
                0,
                actorState.getCurrentContent(),
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage(request.getInstruction()),
                        new ChatMessage.AiChatMessage(reviewSeedMessage(actorState, actorSummary, documentToolMode))
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
        messages.add(SystemMessage.from(analysisSystemPrompt(context)));
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
        DocumentToolMode documentToolMode = documentToolMode(context);
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(finalizationSystemPrompt()));
        messages.addAll(memoryChatMessageMapper.toChatMessages(context.getMemory()));
        messages.add(UserMessage.from(finalizationReviewPayload(context, analysisText, documentToolMode)));
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

    private String analysisSystemPrompt(AgentRunContext context) {
        DocumentToolMode documentToolMode = documentToolMode(context);
        String reviewWorkflow = reviewWorkflow(documentToolMode);
        String documentGuidanceSection = documentGuidanceSection(context, documentToolMode);
        return """
                ## Role
                You are a critic for a document editing reflexion workflow.
                Review the current draft against the instruction and decide whether the actor can pass or must revise.

                %s
                ## Workflow
                %s
                You may call tools multiple times until you have enough evidence.

                ## Tool Rules
                Base your judgement on the instruction, the current draft, and the evidence already present in memory.
                Use tools only when they provide additional evidence you do not already have.

                ## Long-Term Memory Rules
                %s

                ## Output Rules
                When you are ready to finish, return critique JSON with:
                - verdict: PASS or REVISE
                - feedback: concise actionable feedback
                - reasoning: concise explanation
                """.formatted(
                documentGuidanceSection,
                reviewWorkflow,
                reviewMemoryRules()
        );
    }

    private String reviewMemoryRules() {
                return """
                Use %s when prior durable document decisions may affect the critique.
                Always treat retrieved DOCUMENT_DECISION memories as constraints when judging the draft.
                Do not write long-term memory from critic agents.
                Use retrieved memory as evidence, but verify against the current instruction and document.
                """.formatted(MemoryToolNames.SEARCH_MEMORY);
    }

    private String finalizationSystemPrompt() {
        return """
                ## Role
                You are a critic for a document editing reflexion workflow.
                Finalize the critique based on the gathered evidence.

                ## Workflow
                Do not call any tools.
                Convert the gathered evidence into a final verdict.

                ## Output Rules
                Return only strict JSON with:
                - verdict: PASS or REVISE
                - feedback: concise actionable feedback
                - reasoning: concise explanation
                """;
    }

    private String reviewWorkflow(DocumentToolMode documentToolMode) {
        if (documentToolMode == DocumentToolMode.INCREMENTAL) {
            return "Use " + DocumentToolNames.READ_DOCUMENT_NODE
                    + " for targeted inspection when the document is too large for a full snapshot.";
        }
        return "Use " + DocumentToolNames.GET_DOCUMENT_SNAPSHOT
                + " when you need the latest whole-document snapshot for review. The current prompt already includes the latest full draft.";
    }

    private String structureJson(AgentRunContext context) {
        if (context.getRequest() == null || context.getRequest().getDocument() == null) {
            return "(no document)";
        }
        return structuredDocumentService.renderStructureJson(
                context.getRequest().getDocument().getTitle(),
                context.getCurrentContent()
        );
    }

    private String documentGuidanceSection(AgentRunContext context, DocumentToolMode documentToolMode) {
        if (documentToolMode == DocumentToolMode.INCREMENTAL) {
            return """
                    ## Document Structure JSON
                    %s

                    """.formatted(structureJson(context));
        }
        // critic 在 full-mode 下直接看到正文，才能保持“小文档沿用全量读取”的原有评审路径。
        return """
                ## Current Document Content
                %s

                """.formatted(context.getCurrentContent() == null ? "" : context.getCurrentContent());
    }

    private String reviewSeedMessage(AgentRunContext context, String actorSummary, DocumentToolMode documentToolMode) {
        if (documentToolMode == DocumentToolMode.INCREMENTAL) {
            return """
                    Document Structure JSON:
                    %s
                    Actor Summary:
                    %s
                    """.formatted(structureJson(context), actorSummary);
        }
        return """
                Current Document Content:
                %s
                Actor Summary:
                %s
                """.formatted(context.getCurrentContent() == null ? "" : context.getCurrentContent(), actorSummary);
    }

    private String finalizationReviewPayload(AgentRunContext context,
                                             String analysisText,
                                             DocumentToolMode documentToolMode) {
        if (documentToolMode == DocumentToolMode.INCREMENTAL) {
            return """
                    Document Structure JSON:
                    %s

                    Draft critique analysis:
                    %s
                    """.formatted(
                    structureJson(context),
                    analysisText == null ? "" : analysisText
            );
        }
        return """
                Current Document Content:
                %s

                Draft critique analysis:
                %s
                """.formatted(
                context.getCurrentContent() == null ? "" : context.getCurrentContent(),
                analysisText == null ? "" : analysisText
        );
    }

    private DocumentToolMode documentToolMode(AgentRunContext context) {
        if (context.getRequest() == null || context.getRequest().getDocumentToolMode() == null) {
            return DocumentToolMode.FULL;
        }
        return context.getRequest().getDocumentToolMode();
    }
}
