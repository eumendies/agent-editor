package com.agent.editor.agent.core.memory;

public interface SessionMemoryStore {

    ChatTranscriptMemory load(String sessionId);

    void save(String sessionId, ChatTranscriptMemory memory);
}
