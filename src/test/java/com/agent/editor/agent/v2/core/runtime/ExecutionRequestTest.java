package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
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

        assertEquals("task-1", request.taskId());
        assertEquals(AgentType.REACT, request.agentType());
        assertEquals("body", request.document().content());
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

        assertEquals(2, state.iteration());
        assertEquals("body", state.currentContent());
        assertEquals(ExecutionStage.RUNNING, state.stage());
        assertEquals(
                List.of("request", "iteration", "currentContent", "memory", "stage", "pendingReason", "toolSpecifications"),
                Arrays.stream(AgentRunContext.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
        );
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

        assertEquals(1, state.iteration());
        assertEquals("body", state.currentContent());
        assertEquals(2, next.iteration());
        assertEquals("updated body", next.currentContent());
        assertEquals(ExecutionStage.RUNNING, next.stage());
        ChatTranscriptMemory transcriptMemory = (ChatTranscriptMemory) next.memory();
        assertTrue(transcriptMemory.messages().stream().anyMatch(message ->
                message instanceof ChatMessage.UserChatMessage userMessage
                        && userMessage.text().contains("step 1")
        ));
    }
}
