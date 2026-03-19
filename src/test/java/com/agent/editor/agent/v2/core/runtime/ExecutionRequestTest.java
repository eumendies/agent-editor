package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.AgentType;
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
    void shouldUseCheckpointShapedExecutionState() {
        ExecutionState state = new ExecutionState(
                2,
                "body",
                new ChatTranscriptMemory(List.of()),
                ExecutionStage.RUNNING,
                null
        );

        assertEquals(2, state.iteration());
        assertEquals("body", state.currentContent());
        assertEquals(ExecutionStage.RUNNING, state.stage());
        assertEquals(
                List.of("iteration", "currentContent", "memory", "stage", "pendingReason"),
                Arrays.stream(ExecutionState.class.getRecordComponents())
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
        ExecutionState state = new ExecutionState(1, "body");

        ExecutionState next = state
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
