package com.agent.editor.agent.memory;

import com.agent.editor.config.MemoryCompressionProperties;
import com.agent.editor.agent.core.agent.ToolCall;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.core.memory.MemoryCompressionRequest;
import com.agent.editor.agent.core.memory.MemoryCompressionResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelBasedMemoryCompressorTest {

    @Test
    void shouldReturnOriginalMemoryWhenObservedTokensAreBelowTrigger() {
        ChatTranscriptMemory memory = transcript(
                1200,
                new ChatMessage.UserChatMessage("turn 1"),
                new ChatMessage.AiChatMessage("turn 2")
        );
        ModelBasedMemoryCompressor compressor = new ModelBasedMemoryCompressor(new RecordingChatModel("unused"));

        MemoryCompressionResult result = compressor.compress(new MemoryCompressionRequest(
                memory,
                2000,
                1600,
                3,
                20
        ));

        assertSame(memory, result.getMemory());
        assertFalse(result.isCompressed());
        assertEquals("below_threshold", result.getReason());
    }

    @Test
    void shouldCompressOlderHistoryIntoSummaryMessagesAndPreserveLatestThreeRawMessages() {
        ChatTranscriptMemory memory = transcript(
                2800,
                new ChatMessage.UserChatMessage("goal"),
                new ChatMessage.AiToolCallChatMessage("searching", List.of(new ToolCall("call-1", "retrieveKnowledge", "{\"query\":\"policy\"}"))),
                new ChatMessage.ToolExecutionResultChatMessage("call-1", "retrieveKnowledge", "{\"query\":\"policy\"}", "found facts"),
                new ChatMessage.AiChatMessage("decision made"),
                new ChatMessage.UserChatMessage("follow up"),
                new ChatMessage.AiChatMessage("latest draft")
        );
        ModelBasedMemoryCompressor compressor = new ModelBasedMemoryCompressor(new RecordingChatModel("""
                Memory summary [1/2]
                - user goals: summarize earlier history
                - tool findings: found facts
                <<<SECTION>>>
                Memory summary [2/2]
                - important decisions: decision made
                - unresolved points: none
                """));

        MemoryCompressionResult result = compressor.compress(new MemoryCompressionRequest(
                memory,
                2000,
                1600,
                3,
                20
        ));

        assertTrue(result.isCompressed());
        ChatTranscriptMemory compressed = result.getMemory();
        assertEquals(5, compressed.getMessages().size());
        assertTrue(compressed.getMessages().get(0).getText().contains("[Compressed Memory Summary 1/2]"));
        assertTrue(compressed.getMessages().get(0).getText().contains("Memory summary [1/2]"));
        assertTrue(compressed.getMessages().get(1).getText().contains("[Compressed Memory Summary 2/2]"));
        assertTrue(compressed.getMessages().get(1).getText().contains("Memory summary [2/2]"));
        assertEquals("decision made", compressed.getMessages().get(2).getText());
        assertEquals("follow up", compressed.getMessages().get(3).getText());
        assertEquals("latest draft", compressed.getMessages().get(4).getText());
        assertEquals(2800, compressed.getLastObservedTotalTokens());
    }

    @Test
    void shouldFallBackToOriginalMemoryWhenSummarizationFails() {
        ChatTranscriptMemory memory = transcript(
                2800,
                new ChatMessage.UserChatMessage("turn 1"),
                new ChatMessage.AiChatMessage("turn 2"),
                new ChatMessage.UserChatMessage("turn 3"),
                new ChatMessage.AiChatMessage("turn 4")
        );
        ModelBasedMemoryCompressor compressor = new ModelBasedMemoryCompressor(new FailingChatModel());

        MemoryCompressionResult result = compressor.compress(new MemoryCompressionRequest(
                memory,
                2000,
                1600,
                3,
                20
        ));

        assertSame(memory, result.getMemory());
        assertFalse(result.isCompressed());
        assertEquals("summarization_failed", result.getReason());
    }

    @Test
    void shouldCompressWhenObservedTokensAreMissingButMessageCountExceedsFallbackLimit() {
        ChatTranscriptMemory memory = transcript(
                null,
                new ChatMessage.UserChatMessage("turn 1"),
                new ChatMessage.AiChatMessage("turn 2"),
                new ChatMessage.UserChatMessage("turn 3"),
                new ChatMessage.AiChatMessage("turn 4"),
                new ChatMessage.UserChatMessage("turn 5")
        );
        ModelBasedMemoryCompressor compressor = new ModelBasedMemoryCompressor(
                new RecordingChatModel("Memory summary [1/1]\n- user goals: keep context compact")
        );

        MemoryCompressionResult result = compressor.compress(new MemoryCompressionRequest(
                memory,
                2000,
                1600,
                3,
                4
        ));

        assertTrue(result.isCompressed());
        ChatTranscriptMemory compressed = result.getMemory();
        assertEquals(4, compressed.getMessages().size());
        assertInstanceOf(ChatMessage.AiChatMessage.class, compressed.getMessages().get(0));
        assertEquals("turn 3", compressed.getMessages().get(1).getText());
        assertEquals("turn 4", compressed.getMessages().get(2).getText());
        assertEquals("turn 5", compressed.getMessages().get(3).getText());
    }

    @Test
    void shouldUseConfiguredDefaultsWhenRequestDoesNotSpecifyThresholds() {
        ChatTranscriptMemory memory = transcript(
                1500,
                new ChatMessage.UserChatMessage("turn 1"),
                new ChatMessage.AiChatMessage("turn 2"),
                new ChatMessage.UserChatMessage("turn 3"),
                new ChatMessage.AiChatMessage("turn 4")
        );
        ModelBasedMemoryCompressor compressor = new ModelBasedMemoryCompressor(
                new RecordingChatModel("Memory summary [1/1]\n- important decisions: summarized"),
                new MemoryCompressionProperties(1000, 2, 99)
        );

        MemoryCompressionResult result = compressor.compress(new MemoryCompressionRequest(
                memory,
                null,
                null,
                0,
                null
        ));

        assertTrue(result.isCompressed());
        ChatTranscriptMemory compressed = result.getMemory();
        assertEquals(3, compressed.getMessages().size());
        assertEquals("turn 3", compressed.getMessages().get(1).getText());
        assertEquals("turn 4", compressed.getMessages().get(2).getText());
    }

    private static ChatTranscriptMemory transcript(Integer totalTokens, ChatMessage... messages) {
        ChatTranscriptMemory memory = new ChatTranscriptMemory(List.of(messages));
        memory.setLastObservedTotalTokens(totalTokens);
        return memory;
    }

    private static final class RecordingChatModel implements ChatModel {

        private final String responseText;

        private RecordingChatModel(String responseText) {
            this.responseText = responseText;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(responseText))
                    .build();
        }
    }

    private static final class FailingChatModel implements ChatModel {

        @Override
        public ChatResponse chat(ChatRequest request) {
            throw new IllegalStateException("model unavailable");
        }
    }
}
