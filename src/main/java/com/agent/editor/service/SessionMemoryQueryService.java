package com.agent.editor.service;

import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.SessionMemoryStore;
import com.agent.editor.dto.SessionMemoryMessageResponse;
import com.agent.editor.dto.SessionMemoryResponse;
import com.agent.editor.dto.SessionMemoryToolCallResponse;
import org.springframework.stereotype.Service;

@Service
public class SessionMemoryQueryService {

    private final SessionMemoryStore sessionMemoryStore;

    public SessionMemoryQueryService(SessionMemoryStore sessionMemoryStore) {
        this.sessionMemoryStore = sessionMemoryStore;
    }

    public SessionMemoryResponse getSessionMemory(String sessionId) {
        ChatTranscriptMemory memory = sessionMemoryStore.load(sessionId);
        SessionMemoryResponse response = new SessionMemoryResponse();
        response.setSessionId(sessionId);
        response.setMessages(memory.messages().stream()
                .map(this::toMessageResponse)
                .toList());
        response.setMessageCount(response.getMessages().size());
        return response;
    }

    private SessionMemoryMessageResponse toMessageResponse(ChatMessage message) {
        SessionMemoryMessageResponse response = new SessionMemoryMessageResponse();
        response.setText(message.text());

        if (message instanceof ChatMessage.UserChatMessage) {
            response.setType("USER");
            return response;
        }
        if (message instanceof ChatMessage.AiChatMessage) {
            response.setType("AI");
            return response;
        }
        if (message instanceof ChatMessage.AiToolCallChatMessage aiToolCallMessage) {
            response.setType("AI_TOOL_CALL");
            response.setToolCalls(aiToolCallMessage.toolCalls().stream()
                    .map(toolCall -> {
                        SessionMemoryToolCallResponse toolCallResponse = new SessionMemoryToolCallResponse();
                        toolCallResponse.setToolCallId(toolCall.id());
                        toolCallResponse.setToolName(toolCall.name());
                        toolCallResponse.setArguments(toolCall.arguments());
                        return toolCallResponse;
                    })
                    .toList());
            return response;
        }
        if (message instanceof ChatMessage.ToolExecutionResultChatMessage toolResultMessage) {
            response.setType("TOOL_RESULT");
            response.setToolCallId(toolResultMessage.id());
            response.setToolName(toolResultMessage.name());
            response.setArguments(toolResultMessage.argument());
            return response;
        }
        throw new IllegalStateException("Unsupported chat message type: " + message.getClass().getSimpleName());
    }
}
