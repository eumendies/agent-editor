package com.agent.editor.agent.react;

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
import com.agent.editor.agent.tool.document.DocumentToolMode;
import com.agent.editor.agent.tool.document.DocumentToolNames;
import com.agent.editor.agent.tool.memory.MemoryToolNames;
import com.agent.editor.service.StructuredDocumentService;
import dev.langchain4j.data.message.SystemMessage;

import java.util.ArrayList;
import java.util.List;

public class ReactAgentContextFactory implements AgentContextFactory, MemoryCompressionCapableContextFactory {

    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;
    private final MemoryCompressor memoryCompressor;
    private final StructuredDocumentService structuredDocumentService;

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
        DocumentToolMode documentToolMode = documentToolMode(context);
        String documentGuidanceSection = documentGuidanceSection(context, documentToolMode);
        String workflow = documentToolMode == DocumentToolMode.INCREMENTAL
                ? """
                1. Analyze the user's instruction.
                2. Inspect the structure JSON and identify the target section by nodeId before reading content.
                3. Take ONE action at a time using the available tools when an action is needed.
                4. Observe the result of that action.
                5. Decide whether to continue with another action or finish.
                """
                : """
                1. Analyze the user's instruction.
                2. Inspect the current document with the available whole-document tools before making broad edits.
                3. Take ONE action at a time using the available tools when an action is needed.
                4. Observe the result of that action.
                5. Decide whether to continue with another action or finish.
                """;
        String toolRules = documentToolMode == DocumentToolMode.INCREMENTAL
                ? """
                Prefer %s for targeted reads.
                Prefer %s for targeted writes.
                Read the relevant node or block before modifying it.
                If the requested location is unclear, inspect the structure JSON first and then choose the smallest affected section.
                """
                .formatted(
                        DocumentToolNames.READ_DOCUMENT_NODE,
                        DocumentToolNames.PATCH_DOCUMENT_NODE
                )
                : """
                Use %s to inspect the latest full document state before broad rewrites.
                Use the available whole-document write tools only when the requested change is document-wide or naturally spans many sections.
                Prefer the smallest effective write and avoid unnecessary full rewrites.
                """
                .formatted(DocumentToolNames.GET_DOCUMENT_SNAPSHOT);
        return """
                ## Role
                You are a ReAct-style document editing agent.
                Your primary job is to update the current document when the user asks you to write.

                %s
                %s
                ## Workflow
                Think step by step:
                %s

                ## Tool Rules
                %s

                ## Long-Term Memory Rules
                %s

                ## Forbidden Actions
                Do not draft document content directly in chat when the user expects the document to be updated.
                Do not default to rewriting the entire document unless the user's instruction clearly requires a full rewrite.
                Do not patch a node or block that you have not inspected in the current context.

                ## Output Rules
                Only reply directly in chat when the user explicitly wants you to explain, analyze, answer questions, or discuss options without editing the document.
                After completing a document-writing task, keep your final text concise and only confirm that the document was updated.
                """.formatted(
                documentGuidanceSection,
                profileGuidanceSection(context),
                workflow,
                toolRules,
                writeMemoryRules()
        );
    }

    private String writeMemoryRules() {
        return """
                Use %s before writing when prior durable document decisions may affect the edit.
                Use %s only for durable DOCUMENT_DECISION memories that should constrain future work on this document.
                Do not write USER_PROFILE memories from autonomous document-writing agents.
                Do not store execution logs, one-off edits, temporary plans, or tool traces as long-term memory.
                When a decision changes, prefer updating or deleting the existing memory instead of creating duplicates.
                """.formatted(MemoryToolNames.SEARCH_MEMORY, MemoryToolNames.UPSERT_MEMORY);
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
                    You must use the nodeId values from the JSON structure when calling document read or patch tools.
                    Do not guess nodeId values.

                    ## Document Structure JSON
                    %s

                    """.formatted(structureJson(context));
        }
        // full-mode 只给正文，避免把 nodeId 这类增量概念混进整文编辑路径。
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
