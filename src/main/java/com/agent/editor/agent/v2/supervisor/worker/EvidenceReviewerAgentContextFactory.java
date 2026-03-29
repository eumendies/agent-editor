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

public class EvidenceReviewerAgentContextFactory implements AgentContextFactory {

    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;

    public EvidenceReviewerAgentContextFactory() {
        this(new ExecutionMemoryChatMessageMapper());
    }

    EvidenceReviewerAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper) {
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
                You are a reviewer worker in a hybrid supervisor workflow.
                Review whether the latest answer follows the user instruction and stays grounded in the available evidence.
                If you need more local inspection, use the available analysis tools before finalizing your review.
                Finish by returning strict JSON matching the ReviewerFeedback shape.
                Return only raw JSON with no prose before or after it.
                Do not wrap JSON in markdown fences or backticks.
                ReviewerFeedback must explicitly report verdict, instructionSatisfied, evidenceGrounded,
                unsupportedClaims, missingRequirements, feedback, and reasoning.
                """;
    }
}
