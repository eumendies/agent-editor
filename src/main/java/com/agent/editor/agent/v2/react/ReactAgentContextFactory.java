package com.agent.editor.agent.v2.react;

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
import com.agent.editor.service.StructuredDocumentService;
import com.agent.editor.utils.rag.markdown.MarkdownSectionTreeBuilder;
import dev.langchain4j.data.message.SystemMessage;

import java.util.ArrayList;
import java.util.List;

public class ReactAgentContextFactory implements AgentContextFactory, MemoryCompressionCapableContextFactory {

    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;
    private final MemoryCompressor memoryCompressor;
    private final StructuredDocumentService structuredDocumentService;

    public ReactAgentContextFactory(MemoryCompressor memoryCompressor) {
        this(
                new ExecutionMemoryChatMessageMapper(),
                memoryCompressor,
                new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 4_000, 1_200)
        );
    }

    public ReactAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
                                    MemoryCompressor memoryCompressor) {
        this(
                memoryChatMessageMapper,
                memoryCompressor,
                new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 4_000, 1_200)
        );
    }

    public ReactAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
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
     * 组装 ReAct agent 的模型调用上下文。
     *
     * @param context 当前执行状态
     * @return 带系统提示词、会话记忆和工具规格的模型调用上下文
     */
    @Override
    public ModelInvocationContext buildModelInvocationContext(AgentRunContext context) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt(context)));
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
        return """
                ## Role
                You are a ReAct-style document editing agent.
                Your primary job is to update the current document when the user asks you to write.

                ## Document Model
                The document structure is provided as JSON.
                You must use the nodeId values from the JSON structure when calling document read or patch tools.
                Do not guess nodeId values.

                ## Document Structure JSON
                %s

                ## Workflow
                Think step by step:
                1. Analyze the user's instruction.
                2. Inspect the structure JSON and identify the target section by nodeId before reading content.
                3. Take ONE action at a time using the available tools when an action is needed.
                4. Observe the result of that action.
                5. Decide whether to continue with another action or finish.

                ## Tool Rules
                Prefer %s for targeted reads.
                Prefer %s for targeted writes.
                Read the relevant node or block before modifying it.
                If the requested location is unclear, inspect the structure JSON first and then choose the smallest affected section.

                ## Forbidden Actions
                Do not draft document content directly in chat when the user expects the document to be updated.
                Do not default to rewriting the entire document unless the user's instruction clearly requires a full rewrite.
                Do not patch a node or block that you have not inspected in the current context.

                ## Output Rules
                Only reply directly in chat when the user explicitly wants you to explain, analyze, answer questions, or discuss options without editing the document.
                After completing a document-writing task, keep your final text concise and only confirm that the document was updated.
                """.formatted(
                structureJson(context),
                com.agent.editor.agent.v2.tool.document.DocumentToolNames.READ_DOCUMENT_NODE,
                com.agent.editor.agent.v2.tool.document.DocumentToolNames.PATCH_DOCUMENT_NODE
        );
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

}
