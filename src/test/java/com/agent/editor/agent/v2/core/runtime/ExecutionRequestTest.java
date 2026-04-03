package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionRequestTest {

    @Test
    void shouldRetainExecutionInputMetadata() {
        ExecutionRequest request = new ExecutionRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "rewrite this",
                6
        );

        assertEquals("task-1", request.getTaskId());
        assertEquals(AgentType.REACT, request.getAgentType());
        assertEquals("body", request.getDocument().getContent());
        assertEquals("", request.getUserProfileGuidance());
    }

    @Test
    void shouldRetainExplicitUserProfileGuidance() {
        ExecutionRequest request = new ExecutionRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "rewrite this",
                6
        );

        request.setUserProfileGuidance("Confirmed user profile:\n- Always answer in Chinese");

        assertTrue(request.getUserProfileGuidance().contains("Always answer in Chinese"));
    }

    @Test
    void shouldUseCheckpointShapedAgentRunContext() {
        AgentRunContext state = new AgentRunContext(
                null,
                2,
                "body",
                new ChatTranscriptMemory(List.of()),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );

        assertEquals(2, state.getIteration());
        assertEquals("body", state.getCurrentContent());
        assertEquals(ExecutionStage.RUNNING, state.getStage());
        assertTrue(state.getToolSpecifications().isEmpty());
    }

    @Test
    void shouldExposeSupportedExecutionStages() {
        assertEquals(
                List.of(
                        ExecutionStage.RUNNING,
                        ExecutionStage.COMPLETED,
                        ExecutionStage.WAITING_FOR_HUMAN,
                        ExecutionStage.FAILED
                ),
                Arrays.asList(ExecutionStage.values())
        );
    }

    @Test
    void shouldAdvanceStateImmutably() {
        AgentRunContext state = new AgentRunContext(1, "body");

        AgentRunContext next = state
                .appendMemory(new ChatMessage.UserChatMessage("step 1"))
                .advance("updated body");

        assertEquals(1, state.getIteration());
        assertEquals("body", state.getCurrentContent());
        assertEquals(2, next.getIteration());
        assertEquals("updated body", next.getCurrentContent());
        assertEquals(ExecutionStage.RUNNING, next.getStage());
        ChatTranscriptMemory transcriptMemory = (ChatTranscriptMemory) next.getMemory();
        assertTrue(transcriptMemory.getMessages().stream().anyMatch(message ->
                message instanceof ChatMessage.UserChatMessage userMessage
                        && userMessage.getText().contains("step 1")
        ));
    }
}
