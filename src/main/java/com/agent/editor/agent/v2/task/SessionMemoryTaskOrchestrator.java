package com.agent.editor.agent.v2.task;

import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.SessionMemoryStore;

public class SessionMemoryTaskOrchestrator implements TaskOrchestrator {

    private final TaskOrchestrator delegate;
    private final SessionMemoryStore sessionMemoryStore;

    public SessionMemoryTaskOrchestrator(TaskOrchestrator delegate, SessionMemoryStore sessionMemoryStore) {
        this.delegate = delegate;
        this.sessionMemoryStore = sessionMemoryStore;
    }

    @Override
    public TaskResult execute(TaskRequest request) {
        ChatTranscriptMemory memory = sessionMemoryStore.load(request.sessionId());
        TaskResult result = delegate.execute(new TaskRequest(
                request.taskId(),
                request.sessionId(),
                request.agentType(),
                request.document(),
                request.instruction(),
                request.maxIterations(),
                memory
        ));
        if (result.memory() instanceof ChatTranscriptMemory transcriptMemory) {
            sessionMemoryStore.save(request.sessionId(), transcriptMemory);
        }
        return result;
    }
}
