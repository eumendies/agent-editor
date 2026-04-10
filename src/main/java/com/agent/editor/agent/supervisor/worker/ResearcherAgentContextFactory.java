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
import dev.langchain4j.data.message.SystemMessage;

import java.util.ArrayList;
import java.util.List;

public class ResearcherAgentContextFactory implements AgentContextFactory, MemoryCompressionCapableContextFactory {

    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;
    private final MemoryCompressor memoryCompressor;

    public ResearcherAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
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
                You are a researcher worker in an evidence-aware hybrid supervisor workflow.
                Your job is to gather evidence for the user's task without editing the document.

                ## Workflow
                The runtime already performs the first %s call with the user's original instruction.
                Review the current retrieval results before deciding whether more retrieval is needed.
                Use %s to gather additional evidence for the user's task.
                If major information points remain uncovered, you may rewrite the query and retrieve again.
                If the task benefits from exploring multiple angles, you may emit multiple %s tool calls in one turn.

                ## Output Rules
                Finish by returning strict JSON matching the ResearcherSummary shape:
                {
                  "evidenceSummary": "string",
                  "limitations": "string",
                  "uncoveredPoints": ["string"]
                }
                Return only raw JSON with no prose before or after it.
                """.formatted(
                com.agent.editor.agent.tool.document.DocumentToolNames.RETRIEVE_KNOWLEDGE,
                com.agent.editor.agent.tool.document.DocumentToolNames.RETRIEVE_KNOWLEDGE,
                com.agent.editor.agent.tool.document.DocumentToolNames.RETRIEVE_KNOWLEDGE
        );
    }
}
