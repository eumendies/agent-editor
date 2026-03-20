package com.agent.editor.agent.v2.memory;

import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.SessionMemoryStore;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemorySessionMemoryStore implements SessionMemoryStore {

    private final ConcurrentMap<String, ChatTranscriptMemory> memoriesBySession = new ConcurrentHashMap<>();

    @Override
    public ChatTranscriptMemory load(String sessionId) {
        return memoriesBySession.getOrDefault(sessionId, new ChatTranscriptMemory(List.of()));
    }

    @Override
    public void save(String sessionId, ChatTranscriptMemory memory) {
        memoriesBySession.put(sessionId, memory);
    }
}
