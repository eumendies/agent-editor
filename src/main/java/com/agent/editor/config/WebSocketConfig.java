package com.agent.editor.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.agent.editor.websocket.AgentWebSocketHandler;
import com.agent.editor.websocket.AgentV2WebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    @Autowired
    private AgentWebSocketHandler agentWebSocketHandler;

    @Autowired
    private AgentV2WebSocketHandler agentV2WebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentWebSocketHandler, "/ws/agent")
                .setAllowedOrigins("*");
        registry.addHandler(agentV2WebSocketHandler, "/ws/agent/v2")
                .setAllowedOrigins("*");
    }
}
