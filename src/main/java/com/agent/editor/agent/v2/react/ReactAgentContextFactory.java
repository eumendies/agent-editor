package com.agent.editor.agent.v2.react;

import com.agent.editor.agent.v2.core.context.AgentContextFactory;
import com.agent.editor.agent.v2.core.context.ModelInvocationContext;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.ExecutionMemory;
import com.agent.editor.agent.v2.core.memory.MemoryCompressor;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.mapper.ExecutionMemoryChatMessageMapper;
import com.agent.editor.agent.v2.task.TaskRequest;
import dev.langchain4j.data.message.SystemMessage;

import java.util.ArrayList;
import java.util.List;

public class ReactAgentContextFactory implements AgentContextFactory {

    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;
    private final MemoryCompressor memoryCompressor;

    public ReactAgentContextFactory(MemoryCompressor memoryCompressor) {
        this(new ExecutionMemoryChatMessageMapper(), memoryCompressor);
    }

    public ReactAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
                                    MemoryCompressor memoryCompressor) {
        this.memoryChatMessageMapper = memoryChatMessageMapper;
        this.memoryCompressor = memoryCompressor;
    }

    @Override
    public AgentRunContext prepareInitialContext(TaskRequest request) {
        return compressContextMemory(new AgentRunContext(
                null,
                0,
                request.getDocument().getContent(),
                appendUserMessage(request.getMemory(), request.getInstruction()),
                ExecutionStage.RUNNING,
                null,
                List.of()
        ));
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

    protected AgentRunContext compressContextMemory(AgentRunContext context) {
        return context.withMemory(memoryCompressor.compressOrOriginal(context.getMemory()));
    }

    private String systemPrompt() {
        return """
                You are a ReAct-style document editing agent.
                Think step by step:
                1. Analyze the user's instruction.
                2. Take ONE action at a time using the available tools when an action is needed.
                3. Observe the result of that action.
                4. Decide whether to continue with another action or finish.
                Your primary job is to update the current document when the user asks you to write.
                If the user asks you to write, draft, rewrite, expand, polish, or generate content, you must call editDocument instead of returning the drafted content directly in chat.
                If the user does not specify a target location, generate the full updated document and use editDocument to overwrite the entire document.
                Use appendToDocument when you only need to append to the end of the current document.
                Use getDocumentSnapshot when you need the latest full document content after prior tool effects.
                Only reply directly in chat when the user explicitly wants you to explain, analyze, answer questions, or discuss options without editing the document.
                After completing a document-writing task, keep your final text concise and only confirm that the document was updated.
                """;
    }

}
