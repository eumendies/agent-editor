package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.context.AgentContextFactory;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.context.ModelInvocationContext;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.mapper.ExecutionMemoryChatMessageMapper;
import com.agent.editor.agent.v2.task.TaskRequest;
import dev.langchain4j.data.message.SystemMessage;

import java.util.ArrayList;
import java.util.List;

public class ResearcherAgentContextFactory implements AgentContextFactory {

    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;

    public ResearcherAgentContextFactory() {
        this(new ExecutionMemoryChatMessageMapper());
    }

    ResearcherAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper) {
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
        messages.addAll(memoryChatMessageMapper.toChatMessages(context.state().getMemory()));
        return new ModelInvocationContext(messages, context.getToolSpecifications(), null);
    }

    private String systemPrompt() {
        return """
                You are a researcher worker in an evidence-aware hybrid supervisor workflow.
                Use retrieveKnowledge to gather evidence for the user's task.
                You may retry retrieval a small number of times if major information points remain uncovered.
                Do not edit the document.
                Finish by return strict JSON matching the EvidencePackage shape.
                """;
    }
}
