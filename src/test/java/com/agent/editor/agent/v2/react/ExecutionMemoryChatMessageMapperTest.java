package com.agent.editor.agent.v2.react;

import com.agent.editor.agent.v2.core.state.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.ExecutionMemory;
import com.agent.editor.agent.v2.core.state.ExecutionMessage;
import com.agent.editor.agent.v2.mapper.ExecutionMemoryChatMessageMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
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
                new ExecutionMessage.SystemExecutionMessage("system"),
                new ExecutionMessage.UserExecutionMessage("user"),
                new ExecutionMessage.ToolExecutionResultExecutionMessage("tool"),
                new ExecutionMessage.AiExecutionMessage("assistant")
        )));

        assertEquals(4, messages.size());
        assertInstanceOf(SystemMessage.class, messages.get(0));
        assertInstanceOf(UserMessage.class, messages.get(1));
        assertInstanceOf(UserMessage.class, messages.get(2));
        assertInstanceOf(AiMessage.class, messages.get(3));
        assertTrue(((UserMessage) messages.get(2)).singleText().contains("tool"));
    }

    @Test
    void shouldReturnEmptyMessagesForUnsupportedMemory() {
        ExecutionMemoryChatMessageMapper mapper = new ExecutionMemoryChatMessageMapper();

        assertTrue(mapper.toChatMessages(new UnsupportedExecutionMemory()).isEmpty());
    }

    private record UnsupportedExecutionMemory() implements ExecutionMemory {
    }
}
