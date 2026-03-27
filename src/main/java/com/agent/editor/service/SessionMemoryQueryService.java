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
        response.setMessages(memory.getMessages().stream()
                .map(this::toMessageResponse)
                .toList());
        response.setMessageCount(response.getMessages().size());
        return response;
    }

    private SessionMemoryMessageResponse toMessageResponse(ChatMessage message) {
        SessionMemoryMessageResponse response = new SessionMemoryMessageResponse();
        response.setText(message.getText());

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
            response.setToolCalls(aiToolCallMessage.getToolCalls().stream()
                    .map(toolCall -> {
                        SessionMemoryToolCallResponse toolCallResponse = new SessionMemoryToolCallResponse();
                        toolCallResponse.setToolCallId(toolCall.getId());
                        toolCallResponse.setToolName(toolCall.getName());
                        toolCallResponse.setArguments(toolCall.getArguments());
                        return toolCallResponse;
                    })
                    .toList());
            return response;
        }
        if (message instanceof ChatMessage.ToolExecutionResultChatMessage toolResultMessage) {
            response.setType("TOOL_RESULT");
            response.setToolCallId(toolResultMessage.getId());
            response.setToolName(toolResultMessage.getName());
            response.setArguments(toolResultMessage.getArgument());
            return response;
        }
        throw new IllegalStateException("Unsupported chat message type: " + message.getClass().getSimpleName());
    }
}
