package com.agent.editor.agent.v2.task;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.ToolLoopAgent;
import com.agent.editor.agent.v2.core.agent.ToolLoopDecision;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.runtime.ToolLoopExecutionRuntime;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.react.ReActAgentOrchestrator;
import com.agent.editor.agent.v2.react.ReactAgentContextFactory;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.agent.v2.support.NoOpMemoryCompressors;
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
                event -> {}
        );
        Agent agent = new StubAgent();
        ReActAgentOrchestrator orchestrator = new ReActAgentOrchestrator(
                runtime,
                agent,
                new ReactAgentContextFactory(NoOpMemoryCompressors.noop())
        );

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

        assertEquals(TaskStatus.COMPLETED, result.getStatus());
        assertEquals("body", result.getFinalContent());
        ChatTranscriptMemory memory = (ChatTranscriptMemory) result.getMemory();
        assertTrue(memory.getMessages().stream().anyMatch(message -> "previous turn".equals(message.getText())));
        assertTrue(memory.getMessages().stream().anyMatch(message -> "finish".equals(message.getText())));
        assertTrue(memory.getMessages().stream().anyMatch(message -> "done".equals(message.getText())));
    }

    private static final class StubAgent implements ToolLoopAgent {

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public ToolLoopDecision decide(AgentRunContext context) {
            return new ToolLoopDecision.Complete("done", "complete immediately");
        }
    }
}
