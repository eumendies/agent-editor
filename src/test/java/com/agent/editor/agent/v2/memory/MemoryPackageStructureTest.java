package com.agent.editor.agent.v2.memory;

import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.ExecutionMemory;
import com.agent.editor.agent.v2.core.memory.MemoryCompressionRequest;
import com.agent.editor.agent.v2.core.memory.MemoryCompressionResult;
import com.agent.editor.agent.v2.core.memory.MemoryCompressor;
import com.agent.editor.agent.v2.core.memory.SessionMemoryStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals("hello", transcriptMemory.getMessages().get(0).getText());
    }

    @Test
    void shouldExposeCompressionContractFromCoreMemoryPackage() {
        assertEquals("com.agent.editor.agent.v2.core.memory", MemoryCompressor.class.getPackageName());
        assertEquals("com.agent.editor.agent.v2.core.memory", MemoryCompressionRequest.class.getPackageName());
        assertEquals("com.agent.editor.agent.v2.core.memory", MemoryCompressionResult.class.getPackageName());

        ChatTranscriptMemory memory = new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("hello")
        ));
        MemoryCompressionRequest request = new MemoryCompressionRequest(
                memory,
                32000,
                24000,
                3,
                200
        );
        MemoryCompressionResult result = new MemoryCompressionResult(memory, false, "below threshold");

        assertEquals(32000, request.getCompressionTriggerTotalTokens());
        assertEquals(3, request.getPreserveLatestMessageCount());
        assertTrue(!result.isCompressed());
        assertEquals("below threshold", result.getReason());
    }

    @Test
    void shouldExposeCompressOrOriginalDefaultMethodOnMemoryCompressor() {
        ChatTranscriptMemory memory = new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("hello")
        ));
        MemoryCompressor compressor = request -> new MemoryCompressionResult(
                new ChatTranscriptMemory(List.of(new ChatMessage.AiChatMessage("compressed"))),
                true,
                "compressed"
        );

        ExecutionMemory compressed = compressor.compressOrOriginal(memory);

        ChatTranscriptMemory transcriptMemory = assertInstanceOf(ChatTranscriptMemory.class, compressed);
        assertEquals(1, transcriptMemory.getMessages().size());
        assertEquals("compressed", transcriptMemory.getMessages().get(0).getText());
    }
}
