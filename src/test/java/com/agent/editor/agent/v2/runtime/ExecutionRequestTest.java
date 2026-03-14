package com.agent.editor.agent.v2.runtime;

import com.agent.editor.agent.v2.definition.AgentType;
import com.agent.editor.agent.v2.state.DocumentSnapshot;
import org.junit.jupiter.api.Test;

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
}
