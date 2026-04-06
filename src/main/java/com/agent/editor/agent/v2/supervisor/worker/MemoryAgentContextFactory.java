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
import com.agent.editor.agent.v2.tool.memory.MemoryToolNames;
import dev.langchain4j.data.message.SystemMessage;

import java.util.ArrayList;
import java.util.List;

public class MemoryAgentContextFactory implements AgentContextFactory, MemoryCompressionCapableContextFactory {

    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;
    private final MemoryCompressor memoryCompressor;

    public MemoryAgentContextFactory(MemoryCompressor memoryCompressor) {
        this(new ExecutionMemoryChatMessageMapper(), memoryCompressor);
    }

    public MemoryAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
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

    @Override
    public ModelInvocationContext buildModelInvocationContext(AgentRunContext context) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt()));
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

    private String systemPrompt() {
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
                """.formatted(MemoryToolNames.SEARCH_MEMORY, MemoryToolNames.UPSERT_MEMORY);
    }
}
