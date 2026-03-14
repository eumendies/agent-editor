package com.agent.editor.agent.v2.runtime;

import com.agent.editor.agent.v2.definition.AgentDefinition;
import com.agent.editor.agent.v2.definition.AgentType;
import com.agent.editor.agent.v2.definition.Decision;
import com.agent.editor.agent.v2.state.DocumentSnapshot;
import com.agent.editor.agent.v2.state.ExecutionState;
import com.agent.editor.agent.v2.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultExecutionRuntimeTest {

    @Test
    void shouldCompleteWhenAgentReturnsCompleteDecision() {
        AgentDefinition agent = new CompletingAgentDefinition();
        ExecutionRuntime runtime = new DefaultExecutionRuntime(new ToolRegistry(), event -> {});
        ExecutionRequest request = new ExecutionRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "finish",
                3
        );

        ExecutionResult result = runtime.run(agent, request);

        assertEquals("done", result.finalMessage());
    }

    private static final class CompletingAgentDefinition implements AgentDefinition {

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public Decision decide(ExecutionContext context) {
            return new Decision.Complete("done", "complete immediately");
        }
    }
}
