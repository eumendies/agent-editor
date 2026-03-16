package com.agent.editor.agent;

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
        return new ReActAgent(chatModel, websocketService);
    }

    public AgentExecutor getDefaultAgent() {
        return new ReActAgent(chatModel, websocketService);
    }

    public boolean isModeSupported(AgentMode mode) {
        return true;
    }
}
