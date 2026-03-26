package com.agent.editor.agent.v2.task;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.runtime.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.runtime.ToolLoopExecutionRuntime;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.react.ReActAgentOrchestrator;
import com.agent.editor.agent.v2.trace.DefaultTraceCollector;
import com.agent.editor.agent.v2.trace.InMemoryTraceStore;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.agent.v2.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleAgentOrchestratorTest {

    @Test
    void shouldReturnTaskResultForSingleAgentExecution() {
        ExecutionRuntime runtime = new ToolLoopExecutionRuntime(
                new ToolRegistry(),
                event -> {},
                new DefaultTraceCollector(new InMemoryTraceStore())
        );
        AgentDefinition agent = new StubAgentDefinition();
        ReActAgentOrchestrator orchestrator = new ReActAgentOrchestrator(runtime, agent);

        TaskRequest request = new TaskRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "finish",
                3,
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("previous turn")
                ))
        );

        TaskResult result = orchestrator.execute(request);

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals("body", result.finalContent());
        ChatTranscriptMemory memory = (ChatTranscriptMemory) result.memory();
        assertTrue(memory.messages().stream().anyMatch(message -> "previous turn".equals(message.text())));
        assertTrue(memory.messages().stream().anyMatch(message -> "finish".equals(message.text())));
        assertTrue(memory.messages().stream().anyMatch(message -> "done".equals(message.text())));
    }

    private static final class StubAgentDefinition implements AgentDefinition {

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public Decision decide(AgentRunContext context) {
            return new Decision.Complete("done", "complete immediately");
        }
    }
}
