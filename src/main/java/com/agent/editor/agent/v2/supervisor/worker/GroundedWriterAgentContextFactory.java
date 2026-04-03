package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.context.AgentContextFactory;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.context.CompressContextMemory;
import com.agent.editor.agent.v2.core.context.MemoryCompressionCapableContextFactory;
import com.agent.editor.agent.v2.core.context.ModelInvocationContext;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.ExecutionMemory;
import com.agent.editor.agent.v2.core.memory.MemoryCompressor;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.mapper.ExecutionMemoryChatMessageMapper;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.service.StructuredDocumentService;
import com.agent.editor.utils.rag.markdown.MarkdownSectionTreeBuilder;
import dev.langchain4j.data.message.SystemMessage;

import java.util.ArrayList;
import java.util.List;

public class GroundedWriterAgentContextFactory implements AgentContextFactory, MemoryCompressionCapableContextFactory {

    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;
    private final MemoryCompressor memoryCompressor;
    private final StructuredDocumentService structuredDocumentService;

    public GroundedWriterAgentContextFactory(MemoryCompressor memoryCompressor) {
        this(
                new ExecutionMemoryChatMessageMapper(),
                memoryCompressor,
                new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 4_000, 1_200)
        );
    }

    public GroundedWriterAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
                                             MemoryCompressor memoryCompressor) {
        this(
                memoryChatMessageMapper,
                memoryCompressor,
                new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 4_000, 1_200)
        );
    }

    public GroundedWriterAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
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
                ## Role
                You are a grounded writer worker in a hybrid supervisor workflow.
                Write or revise the document using only the available context and retrieved evidence in memory.

                ## Document Model
                The document is managed as a structured outline.
                Inspect the structure summary before editing.
                Current document structure:
                %s

                ## Workflow
                1. Inspect the structure summary and locate the smallest section that needs changes.
                2. Use %s to read the relevant node or block before editing.
                3. Use %s to update only the sections you inspected.
                4. Stop once the requested update is complete.

                ## Evidence Constraints
                Do not introduce claims that are not supported by the evidence already present in the conversation.
                If searchContent is available, use it only to inspect the current draft before editing.

                ## Tool Rules
                Prefer targeted node reads and targeted node patches over whole-document rewrites.
                If the target location is ambiguous, inspect structure first and then choose the smallest affected section.

                ## Forbidden Actions
                Do not output draft document content directly in chat when the document should be updated.
                Do not modify a node or block that you have not inspected in the current context.

                ## Output Rules
                Keep your final text concise once the document update is complete.
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
                context.getRequest().getDocument().getTitle(),
                context.getCurrentContent()
        );
    }
}
