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
import com.agent.editor.agent.tool.document.DocumentToolMode;
import com.agent.editor.agent.tool.document.DocumentToolNames;
import com.agent.editor.agent.tool.memory.MemoryToolNames;
import com.agent.editor.service.StructuredDocumentService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.List;

public class EvidenceReviewerAgentContextFactory implements AgentContextFactory, MemoryCompressionCapableContextFactory {

    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;
    private final MemoryCompressor memoryCompressor;
    private final StructuredDocumentService structuredDocumentService;

    public EvidenceReviewerAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
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
        messages.add(UserMessage.from(documentStateMessage(context)));
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
        String reviewWorkflow = documentToolMode == DocumentToolMode.INCREMENTAL
                ? "Use %s for targeted reads when the document is too large for a full snapshot."
                        .formatted(DocumentToolNames.READ_DOCUMENT_NODE)
                : "Use %s when you need the latest full document snapshot for review."
                        .formatted(DocumentToolNames.GET_DOCUMENT_SNAPSHOT);
        return """
                ## Role
                You are a reviewer worker in a hybrid supervisor workflow.
                Review whether the latest answer follows the user instruction and stays grounded in the available evidence.

                ## Workflow
                %s
                If you need more local inspection, use the available analysis tools before finalizing your review.
                Base your verdict on the instruction, the latest content, and the evidence available in memory.

                ## Long-Term Memory Rules
                %s

                ## Output Rules
                Finish by returning exactly one raw JSON object that matches the ReviewerFeedback format.
                Return only raw JSON with no prose before or after it.
                Do not wrap JSON in markdown fences or backticks.
                Do not output comments, bullet lists, headings, or trailing commas.
                Do not omit any field.

                Required JSON fields:
                - "verdict":"PASS" or "REVISE"
                - "instructionSatisfied": true or false
                - "evidenceGrounded": true or false
                - "unsupportedClaims": []
                - "missingRequirements": []
                - "feedback": "short review summary"
                - "reasoning": "why this verdict was chosen"

                Field rules:
                - unsupportedClaims must always be a JSON array of strings, even when empty.
                - missingRequirements must always be a JSON array of strings, even when empty.
                - feedback must always be a JSON string.
                - reasoning must always be a JSON string.

                Valid output example:
                {"verdict":"REVISE","instructionSatisfied":false,"evidenceGrounded":true,"unsupportedClaims":[],"missingRequirements":["Explain project value"],"feedback":"The draft misses a required point.","reasoning":"The answer is grounded but does not fully satisfy the instruction."}
                """.formatted(
                reviewWorkflow,
                reviewMemoryRules()
        );
    }

    private String reviewMemoryRules() {
                return """
                Use %s when prior durable document decisions may affect the review.
                Always treat retrieved DOCUMENT_DECISION memories as constraints when judging the draft.
                Do not write long-term memory from review agents.
                Use retrieved memory as evidence, but verify against the current instruction and document.
                """.formatted(MemoryToolNames.SEARCH_MEMORY);
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

    private String documentStateMessage(AgentRunContext context) {
        DocumentToolMode documentToolMode = documentToolMode(context);
        if (documentToolMode == DocumentToolMode.INCREMENTAL) {
            return """
                    ## Document Model
                    The document structure is provided as JSON.
                    Use the nodeId values from the JSON structure when you need targeted reads.

                    ## Document Structure JSON
                    %s

                    """.formatted(structureJson(context));
        }
        // reviewer 在 full-mode 下应直接看到正文，避免为普通文档多消耗一次快照工具调用。
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
}
