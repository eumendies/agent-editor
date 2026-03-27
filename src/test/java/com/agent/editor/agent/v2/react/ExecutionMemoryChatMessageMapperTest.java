package com.agent.editor.agent.v2.react;

import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.ExecutionMemory;
import com.agent.editor.agent.v2.core.agent.ToolCall;
import com.agent.editor.agent.v2.mapper.ExecutionMemoryChatMessageMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.Data;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionMemoryChatMessageMapperTest {

    @Test
    void shouldConvertTranscriptMemoryToChatMessages() {
        ExecutionMemoryChatMessageMapper mapper = new ExecutionMemoryChatMessageMapper();

        var messages = mapper.toChatMessages(new ChatTranscriptMemory(List.of(
                new ChatMessage.SystemChatMessage("system"),
                new ChatMessage.UserChatMessage("user"),
                new ChatMessage.ToolExecutionResultChatMessage(
                        "tool-call-1",
                        "searchContent",
                        "{\"query\":\"tool\"}",
                        "tool"
                ),
                new ChatMessage.AiChatMessage("assistant")
        )));

        assertEquals(4, messages.size());
        assertInstanceOf(SystemMessage.class, messages.get(0));
        assertInstanceOf(UserMessage.class, messages.get(1));
        ToolExecutionResultMessage toolMessage = assertInstanceOf(ToolExecutionResultMessage.class, messages.get(2));
        assertInstanceOf(AiMessage.class, messages.get(3));
        assertEquals("tool-call-1", toolMessage.id());
        assertEquals("searchContent", toolMessage.toolName());
        assertEquals("tool", toolMessage.text());
    }

    @Test
    void shouldConvertAiToolCallExecutionMessageToAiMessageWithToolRequests() {
        ExecutionMemoryChatMessageMapper mapper = new ExecutionMemoryChatMessageMapper();

        var messages = mapper.toChatMessages(new ChatTranscriptMemory(List.of(
                new ChatMessage.AiToolCallChatMessage(
                        "need tool",
                        List.of(new ToolCall("tool-call-1", "searchContent", "{\"query\":\"heading\"}"))
                )
        )));

        AiMessage aiMessage = assertInstanceOf(AiMessage.class, messages.get(0));
        assertTrue(aiMessage.hasToolExecutionRequests());
        assertEquals("need tool", aiMessage.text());
        assertEquals(1, aiMessage.toolExecutionRequests().size());
        assertEquals("tool-call-1", aiMessage.toolExecutionRequests().get(0).id());
        assertEquals("searchContent", aiMessage.toolExecutionRequests().get(0).name());
        assertEquals("{\"query\":\"heading\"}", aiMessage.toolExecutionRequests().get(0).arguments());
    }

    @Test
    void shouldReturnEmptyMessagesForUnsupportedMemory() {
        ExecutionMemoryChatMessageMapper mapper = new ExecutionMemoryChatMessageMapper();

        assertTrue(mapper.toChatMessages(new UnsupportedExecutionMemory()).isEmpty());
    }

    @Data
    private static class UnsupportedExecutionMemory implements ExecutionMemory {
    }
}
