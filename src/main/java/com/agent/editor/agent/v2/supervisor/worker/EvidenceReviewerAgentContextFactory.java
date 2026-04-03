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

public class EvidenceReviewerAgentContextFactory implements AgentContextFactory, MemoryCompressionCapableContextFactory {

    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;
    private final MemoryCompressor memoryCompressor;
    private final StructuredDocumentService structuredDocumentService;

    public EvidenceReviewerAgentContextFactory(MemoryCompressor memoryCompressor) {
        this(
                new ExecutionMemoryChatMessageMapper(),
                memoryCompressor,
                new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 4_000, 1_200)
        );
    }

    public EvidenceReviewerAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
                                               MemoryCompressor memoryCompressor) {
        this(
                memoryChatMessageMapper,
                memoryCompressor,
                new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 4_000, 1_200)
        );
    }

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
                You are a reviewer worker in a hybrid supervisor workflow.
                Review whether the latest answer follows the user instruction and stays grounded in the available evidence.

                ## Document Model
                The document is managed as a structured outline.
                Current document structure:
                %s

                ## Workflow
                Use %s for targeted reads when the document is too large for a full snapshot.
                If you need more local inspection, use the available analysis tools before finalizing your review.
                Base your verdict on the instruction, the latest content, and the evidence available in memory.

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
                structureSummary(context),
                com.agent.editor.agent.v2.tool.document.DocumentToolNames.READ_DOCUMENT_NODE
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
