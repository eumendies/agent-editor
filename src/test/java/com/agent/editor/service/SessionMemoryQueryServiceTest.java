package com.agent.editor.service;

import com.agent.editor.agent.v2.core.agent.ToolCall;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.memory.InMemorySessionMemoryStore;
import com.agent.editor.dto.SessionMemoryMessageResponse;
import com.agent.editor.dto.SessionMemoryResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SessionMemoryQueryServiceTest {

    @Test
    void shouldMapSessionMemoryToStructuredResponse() {
        InMemorySessionMemoryStore store = new InMemorySessionMemoryStore();
        store.save("session-1", new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("rewrite this"),
                new ChatMessage.AiChatMessage("need tool"),
                new ChatMessage.AiToolCallChatMessage(
                        "calling tools",
                        List.of(new ToolCall("tool-call-1", "searchContent", "{\"query\":\"heading\"}"))
                ),
                new ChatMessage.ToolExecutionResultChatMessage(
                        "tool-call-1",
                        "searchContent",
                        "{\"query\":\"heading\"}",
                        "found heading"
                )
        )));
        SessionMemoryQueryService service = new SessionMemoryQueryService(store);

        SessionMemoryResponse response = service.getSessionMemory("session-1");

        assertEquals("session-1", response.getSessionId());
        assertEquals(4, response.getMessageCount());

        SessionMemoryMessageResponse userMessage = response.getMessages().get(0);
        assertEquals("USER", userMessage.getType());
        assertEquals("rewrite this", userMessage.getText());

        SessionMemoryMessageResponse aiMessage = response.getMessages().get(1);
        assertEquals("AI", aiMessage.getType());
        assertEquals("need tool", aiMessage.getText());

        SessionMemoryMessageResponse toolCallMessage = response.getMessages().get(2);
        assertEquals("AI_TOOL_CALL", toolCallMessage.getType());
        assertEquals("calling tools", toolCallMessage.getText());
        assertEquals(1, toolCallMessage.getToolCalls().size());
        assertEquals("tool-call-1", toolCallMessage.getToolCalls().get(0).getToolCallId());
        assertEquals("searchContent", toolCallMessage.getToolCalls().get(0).getToolName());
        assertEquals("{\"query\":\"heading\"}", toolCallMessage.getToolCalls().get(0).getArguments());

        SessionMemoryMessageResponse toolResultMessage = response.getMessages().get(3);
        assertEquals("TOOL_RESULT", toolResultMessage.getType());
        assertEquals("found heading", toolResultMessage.getText());
        assertEquals("tool-call-1", toolResultMessage.getToolCallId());
        assertEquals("searchContent", toolResultMessage.getToolName());
        assertEquals("{\"query\":\"heading\"}", toolResultMessage.getArguments());
    }

    @Test
    void shouldReturnEmptyResponseForUnknownSession() {
        SessionMemoryQueryService service = new SessionMemoryQueryService(new InMemorySessionMemoryStore());

        SessionMemoryResponse response = service.getSessionMemory("missing");

        assertEquals("missing", response.getSessionId());
        assertEquals(0, response.getMessageCount());
        assertNotNull(response.getMessages());
        assertEquals(0, response.getMessages().size());
    }
}
