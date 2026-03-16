package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.core.state.ExecutionState;
import com.agent.editor.agent.v2.core.state.ChatTranscriptMemory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
