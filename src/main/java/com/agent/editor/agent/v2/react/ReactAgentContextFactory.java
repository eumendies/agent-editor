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
                You are a ReAct-style document editing agent.
                Think step by step:
                1. Analyze the user's instruction.
                2. Take ONE action at a time using the available tools when an action is needed.
                3. Observe the result of that action.
                4. Decide whether to continue with another action or finish.
                Your primary job is to update the current document when the user asks you to write.
                Current document structure:
                %s
                Inspect the structure before reading or editing content.
                Prefer %s for targeted reads and %s for targeted writes.
                If the user asks you to write, draft, rewrite, expand, polish, or generate content, you must call editDocument instead of returning the drafted content directly in chat.
                If the user does not specify a target location, generate the full updated document and use editDocument to overwrite the entire document.
                Use appendToDocument when you only need to append to the end of the current document.
                Use getDocumentSnapshot when you need the latest full document content after prior tool effects.
                Only reply directly in chat when the user explicitly wants you to explain, analyze, answer questions, or discuss options without editing the document.
                After completing a document-writing task, keep your final text concise and only confirm that the document was updated.
                """.formatted(
                structureSummary(context),
                com.agent.editor.agent.v2.tool.document.DocumentToolNames.READ_DOCUMENT_NODE,
                com.agent.editor.agent.v2.tool.document.DocumentToolNames.PATCH_DOCUMENT_NODE
        );
    }

    private String structureSummary(AgentRunContext context) {
        if (context.getRequest() == null || context.getRequest().getDocument() == null) {
            return "(no document)";
        }
        return structuredDocumentService.renderStructureSummary(
                context.getRequest().getDocument().getDocumentId(),
                context.getRequest().getDocument().getTitle(),
                context.getCurrentContent()
        );
    }

}
