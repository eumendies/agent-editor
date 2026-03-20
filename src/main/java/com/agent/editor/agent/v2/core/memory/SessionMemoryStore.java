package com.agent.editor.agent.v2.core.memory;

public interface SessionMemoryStore {

    ChatTranscriptMemory load(String sessionId);

    void save(String sessionId, ChatTranscriptMemory memory);
}
