package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.context.AgentContextFactory;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.context.ModelInvocationContext;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.mapper.ExecutionMemoryChatMessageMapper;
import com.agent.editor.agent.v2.task.TaskRequest;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.List;

public class GroundedWriterAgentContextFactory implements AgentContextFactory {

    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;

    public GroundedWriterAgentContextFactory() {
        this(new ExecutionMemoryChatMessageMapper());
    }

    GroundedWriterAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper) {
        this.memoryChatMessageMapper = memoryChatMessageMapper;
    }

    @Override
    public AgentRunContext prepareInitialContext(TaskRequest request) {
        return new AgentRunContext(
                null,
                0,
                request.getDocument().getContent(),
                request.getMemory(),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );
    }

    @Override
    public ModelInvocationContext buildModelInvocationContext(AgentRunContext context) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt()));
        messages.add(UserMessage.from(context.getRequest().getInstruction()));
        messages.addAll(memoryChatMessageMapper.toChatMessages(context.state().getMemory()));
        return new ModelInvocationContext(messages, context.getToolSpecifications(), null);
    }

    private String systemPrompt() {
        return """
                You are a grounded writer worker in a hybrid supervisor workflow.
                Write or revise the document using only the available context and retrieved evidence in memory.
                Do not introduce claims that are not supported by the evidence already present in the conversation.
                Use editDocument when you need to replace the document content.
                Use appendToDocument when you only need to add content to the end of the current document.
                Use getDocumentSnapshot when you need the latest current document before deciding the next write.
                If searchContent is available, use it only to inspect the current draft before editing.
                Keep your final text concise once the document update is complete.
                """;
    }
}
