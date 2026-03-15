package com.agent.editor.agent.v2.task;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutingTaskOrchestratorTest {

    @Test
    void shouldDelegatePlanningRequestsToPlanningOrchestrator() {
        RecordingOrchestrator react = new RecordingOrchestrator(new TaskResult(TaskStatus.COMPLETED, "react"));
        RecordingOrchestrator planning = new RecordingOrchestrator(new TaskResult(TaskStatus.COMPLETED, "planned"));
        RoutingTaskOrchestrator orchestrator = new RoutingTaskOrchestrator(Map.of(
                AgentType.REACT, react,
                AgentType.PLANNING, planning
        ));

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-1",
                "session-1",
                AgentType.PLANNING,
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve document",
                5
        ));

        assertEquals("planned", result.finalContent());
        assertEquals(0, react.invocations);
        assertEquals(1, planning.invocations);
    }

    private static final class RecordingOrchestrator implements TaskOrchestrator {

        private final TaskResult result;
        private int invocations;

        private RecordingOrchestrator(TaskResult result) {
            this.result = result;
        }

        @Override
        public TaskResult execute(TaskRequest request) {
            invocations++;
            return result;
        }
    }
}
