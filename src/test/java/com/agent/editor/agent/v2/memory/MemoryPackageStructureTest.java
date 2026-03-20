package com.agent.editor.agent.v2.memory;

import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.ExecutionMemory;
import com.agent.editor.agent.v2.core.memory.SessionMemoryStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MemoryPackageStructureTest {

    @Test
    void shouldExposeMemoryAbstractionsFromCoreMemoryPackage() {
        SessionMemoryStore store = new InMemorySessionMemoryStore();
        ChatTranscriptMemory memory = new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("hello")
        ));

        store.save("session-1", memory);
        ExecutionMemory loaded = store.load("session-1");

        ChatTranscriptMemory transcriptMemory = assertInstanceOf(ChatTranscriptMemory.class, loaded);
        assertEquals("hello", transcriptMemory.messages().get(0).text());
    }
}
