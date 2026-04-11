package com.agent.editor.agent.supervisor.worker;

import com.agent.editor.agent.core.context.AgentContextFactory;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.context.CompressContextMemory;
import com.agent.editor.agent.core.context.MemoryCompressionCapableContextFactory;
import com.agent.editor.agent.core.context.ModelInvocationContext;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.core.memory.ExecutionMemory;
import com.agent.editor.agent.core.memory.MemoryCompressor;
import com.agent.editor.agent.core.state.ExecutionStage;
import com.agent.editor.agent.mapper.ExecutionMemoryChatMessageMapper;
import com.agent.editor.agent.task.TaskRequest;
import com.agent.editor.agent.tool.memory.MemoryToolNames;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 为 memory worker 构建初始上下文与模型调用消息。
 * 它负责把现有会话记忆压成可检索、可维护 DOCUMENT_DECISION 的专用提示词视图。
 */
public class MemoryAgentContextFactory implements AgentContextFactory, MemoryCompressionCapableContextFactory {

    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;
    private final MemoryCompressor memoryCompressor;

    public MemoryAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
                                     MemoryCompressor memoryCompressor) {
        this.memoryChatMessageMapper = memoryChatMessageMapper;
        this.memoryCompressor = memoryCompressor;
    }

    /**
     * 创建 memory worker 首轮执行上下文。
     * 这里沿用已有会话记忆，并把当前任务指令追加为最新 user message，保证 memory worker 能同时看到历史摘要和本轮目标。
     *
     * @param request 当前任务请求
     * @return memory worker 的初始运行上下文
     */
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
     * 构建 memory worker 的模型输入。
     * system prompt 负责限定“只管理 DOCUMENT_DECISION”，history 则提供本轮需要参考的上下文证据。
     *
     * @param context 当前运行上下文
     * @return 供模型调用的消息与工具规格
     */
    @Override
    public ModelInvocationContext buildModelInvocationContext(AgentRunContext context) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt(context)));
        messages.add(UserMessage.from(currentDocumentStateMessage(context)));
        messages.addAll(memoryChatMessageMapper.toChatMessages(context.getMemory()));
        return new ModelInvocationContext(messages, context.getToolSpecifications(), null);
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

    private String systemPrompt(AgentRunContext context) {
        // 这里把“何时检索、何时写入、最终如何产出结构化摘要”一次性讲清楚，避免 memory worker 退化成泛化 writer。
        return """
                ## Role
                You are a memory worker in a hybrid supervisor workflow.
                Your job is to retrieve and maintain durable document constraints for the current document.

                ## Allowed Memory Scope
                Manage only DOCUMENT_DECISION memory.
                Never write USER_PROFILE memory.

                ## Workflow
                Use %s to retrieve prior document decisions when they may affect the current task.
                Use %s only when the current task reveals a stable, reusable, rule-style document constraint.
                Prefer replace/delete over duplicate create when an older memory is stale or superseded.

                ## Write Rules
                Store only durable document constraints and confirmed tradeoffs.
                Do not store execution logs, temporary next steps, or one-off edits.
                Use concise summaries that downstream workers can reuse later.

                ## Output Rules
                Finish by returning strict JSON matching the MemoryWorkerSummary shape:
                {
                  "confirmedConstraints": ["string"],
                  "deprecatedConstraints": ["string"],
                  "activeRisks": ["string"],
                  "guidanceForDownstreamWorkers": "string"
                }
                Return only raw JSON with no prose before or after it.
                """.formatted(
                MemoryToolNames.SEARCH_MEMORY,
                MemoryToolNames.UPSERT_MEMORY
        );
    }

    private String currentDocumentStateMessage(AgentRunContext context) {
        return """
                ## Current Document
                documentId: %s
                title: %s
                currentContent:
                %s
                """.formatted(
                context.getDocumentIdOrEmpty(),
                context.getDocumentTitleOrEmpty(),
                context.getCurrentContent() == null ? "" : context.getCurrentContent()
        );
    }
}
