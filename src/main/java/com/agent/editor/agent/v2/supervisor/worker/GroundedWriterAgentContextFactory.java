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
import com.agent.editor.agent.v2.tool.document.DocumentToolMode;
import com.agent.editor.agent.v2.tool.document.DocumentToolNames;
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
        DocumentToolMode documentToolMode = documentToolMode(context);
        String documentGuidanceSection = documentGuidanceSection(context, documentToolMode);
        String workflow = documentToolMode == DocumentToolMode.INCREMENTAL
                ? """
                1. Inspect the structure JSON and locate the smallest section that needs changes.
                2. Use %s to read the relevant node or block before editing.
                3. Use %s to update only the sections you inspected.
                4. Stop once the requested update is complete.
                """.formatted(
                        DocumentToolNames.READ_DOCUMENT_NODE,
                        DocumentToolNames.PATCH_DOCUMENT_NODE
                )
                : """
                1. Inspect the latest full document with %s before broad edits.
                2. Use the available whole-document write tools only when the requested revision naturally spans the whole draft.
                3. Minimize unnecessary rewrites and stop once the requested update is complete.
                """.formatted(DocumentToolNames.GET_DOCUMENT_SNAPSHOT);
        String toolRules = documentToolMode == DocumentToolMode.INCREMENTAL
                ? """
                Prefer targeted node reads and targeted node patches over whole-document rewrites.
                If the target location is ambiguous, inspect the structure JSON first and then choose the smallest affected section.
                """
                : """
                Prefer focused changes even when whole-document tools are visible.
                Use %s to re-check the latest draft before issuing another whole-document write.
                """
                .formatted(DocumentToolNames.GET_DOCUMENT_SNAPSHOT);
        return """
                ## Role
                You are a grounded writer worker in a hybrid supervisor workflow.
                Write or revise the document using only the available context and retrieved evidence in memory.

                %s
                %s
                ## Workflow
                %s

                ## Evidence Constraints
                Do not introduce claims that are not supported by the evidence already present in the conversation.
                If searchContent is available, use it only to inspect the current draft before editing.

                ## Tool Rules
                %s

                ## Forbidden Actions
                Do not output draft document content directly in chat when the document should be updated.
                Do not modify a node or block that you have not inspected in the current context.

                ## Output Rules
                Keep your final text concise once the document update is complete.
                """.formatted(
                documentGuidanceSection,
                profileGuidanceSection(context),
                workflow,
                toolRules
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

    private String documentGuidanceSection(AgentRunContext context, DocumentToolMode documentToolMode) {
        if (documentToolMode == DocumentToolMode.INCREMENTAL) {
            return """
                    ## Document Model
                    The document structure is provided as JSON.
                    You must use the nodeId values from the JSON structure when reading or patching the document.
                    Do not guess nodeId values.

                    ## Document Structure JSON
                    %s

                    """.formatted(structureJson(context));
        }
        // full-mode 保留正文直出，writer 可以沿用旧的整文阅读路径，不需要先额外补一轮工具调用。
        return """
                ## Current Document Content
                %s

                """.formatted(context.getCurrentContent() == null ? "" : context.getCurrentContent());
    }

    private DocumentToolMode documentToolMode(AgentRunContext context) {
        if (context.getRequest() == null || context.getRequest().getDocumentToolMode() == null) {
            return DocumentToolMode.FULL;
        }
        return context.getRequest().getDocumentToolMode();
    }

    private String profileGuidanceSection(AgentRunContext context) {
        if (context.getRequest() == null || context.getRequest().getUserProfileGuidance() == null
                || context.getRequest().getUserProfileGuidance().isBlank()) {
            return "";
        }
        return """
                ## Confirmed User Profile
                %s

                """.formatted(context.getRequest().getUserProfileGuidance());
    }
}
