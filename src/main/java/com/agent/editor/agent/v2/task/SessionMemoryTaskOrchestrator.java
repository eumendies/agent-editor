package com.agent.editor.agent.v2.task;

import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.SessionMemoryStore;

/**
 * 保存对话记忆的任务编排器
 */
public class SessionMemoryTaskOrchestrator implements TaskOrchestrator {

    private final TaskOrchestrator delegate;
    private final SessionMemoryStore sessionMemoryStore;

    public SessionMemoryTaskOrchestrator(TaskOrchestrator delegate, SessionMemoryStore sessionMemoryStore) {
        this.delegate = delegate;
        this.sessionMemoryStore = sessionMemoryStore;
    }

    @Override
    public TaskResult execute(TaskRequest request) {
        ChatTranscriptMemory memory = sessionMemoryStore.load(request.getSessionId());
        TaskResult result = delegate.execute(new TaskRequest(
                request.getTaskId(),
                request.getSessionId(),
                request.getAgentType(),
                request.getDocument(),
                request.getInstruction(),
                request.getMaxIterations(),
                memory
        ));
        if (result.getMemory() instanceof ChatTranscriptMemory transcriptMemory) {
            sessionMemoryStore.save(request.getSessionId(), transcriptMemory);
        }
        return result;
    }
}
