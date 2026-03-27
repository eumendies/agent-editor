package com.agent.editor.agent.v2.task;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.agent.v2.memory.InMemorySessionMemoryStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SessionMemoryTaskOrchestratorTest {

    @Test
    void shouldLoadAndSaveSessionMemoryAroundDelegateExecution() {
        InMemorySessionMemoryStore store = new InMemorySessionMemoryStore();
        ChatTranscriptMemory existingMemory = new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("previous turn")
        ));
        store.save("session-1", existingMemory);

        RecordingOrchestrator delegate = new RecordingOrchestrator(new TaskResult(
                TaskStatus.COMPLETED,
                "rewritten",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("previous turn"),
                        new ChatMessage.AiChatMessage("new answer")
                ))
        ));
        SessionMemoryTaskOrchestrator orchestrator = new SessionMemoryTaskOrchestrator(delegate, store);

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "Title", "body"),
                "rewrite",
                3,
                null
        ));

        ChatTranscriptMemory loadedMemory = assertInstanceOf(ChatTranscriptMemory.class, delegate.lastRequest.getMemory());
        assertEquals(1, loadedMemory.getMessages().size());
        assertEquals("previous turn", loadedMemory.getMessages().get(0).getText());
        assertEquals("rewritten", result.getFinalContent());

        ChatTranscriptMemory savedMemory = store.load("session-1");
        assertEquals(2, savedMemory.getMessages().size());
        assertEquals("new answer", savedMemory.getMessages().get(1).getText());
    }

    private static final class RecordingOrchestrator implements TaskOrchestrator {

        private final TaskResult result;
        private TaskRequest lastRequest;

        private RecordingOrchestrator(TaskResult result) {
            this.result = result;
        }

        @Override
        public TaskResult execute(TaskRequest request) {
            lastRequest = request;
            return result;
        }
    }
}
