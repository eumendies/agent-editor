package com.agent.editor.agent.v2.task;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.runtime.DefaultExecutionRuntime;
import com.agent.editor.agent.v2.core.runtime.ExecutionContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.trace.DefaultTraceCollector;
import com.agent.editor.agent.v2.trace.InMemoryTraceStore;
import com.agent.editor.agent.v2.core.state.TaskStatus;
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
