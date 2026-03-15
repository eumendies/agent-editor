package com.agent.editor.agent.v2.orchestration;

import com.agent.editor.agent.v2.definition.AgentDefinition;
import com.agent.editor.agent.v2.definition.AgentType;
import com.agent.editor.agent.v2.definition.Decision;
import com.agent.editor.agent.v2.runtime.DefaultExecutionRuntime;
import com.agent.editor.agent.v2.runtime.ExecutionContext;
import com.agent.editor.agent.v2.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.state.DocumentSnapshot;
import com.agent.editor.agent.v2.trace.DefaultTraceCollector;
import com.agent.editor.agent.v2.trace.InMemoryTraceStore;
import com.agent.editor.agent.v2.state.TaskStatus;
import com.agent.editor.agent.v2.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SingleAgentOrchestratorTest {

    @Test
    void shouldReturnTaskResultForSingleAgentExecution() {
        ExecutionRuntime runtime = new DefaultExecutionRuntime(
                new ToolRegistry(),
                event -> {},
                new DefaultTraceCollector(new InMemoryTraceStore())
        );
        AgentDefinition agent = new StubAgentDefinition();
        SingleAgentOrchestrator orchestrator = new SingleAgentOrchestrator(runtime, agent);

        TaskRequest request = new TaskRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "finish",
                3
        );

        TaskResult result = orchestrator.execute(request);

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals("body", result.finalContent());
    }

    private static final class StubAgentDefinition implements AgentDefinition {

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
