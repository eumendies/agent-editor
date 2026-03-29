package com.agent.editor.agent.v1;

import com.agent.editor.model.AgentMode;
import com.agent.editor.websocket.WebSocketService;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Deprecated(forRemoval = false)
public class AgentFactory {
    @Autowired
    private ChatModel chatModel;

    @Autowired
    private WebSocketService websocketService;

    public AgentExecutor getAgent(AgentMode mode) {
        return ReActAgent.builder()
                .chatLanguageModel(chatModel)
                .websocketService(websocketService)
                .build();
    }

    public AgentExecutor getDefaultAgent() {
        return ReActAgent.builder()
                .chatLanguageModel(chatModel)
                .websocketService(websocketService)
                .build();
    }

    public boolean isModeSupported(AgentMode mode) {
        return true;
    }
}
