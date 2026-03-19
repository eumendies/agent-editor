package com.agent.editor.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        Server server = new Server();
        server.setUrl("http://localhost:8080");
        server.setDescription("Development Server");
        
        return new OpenAPI()
                .servers(List.of(server))
                .info(new Info()
                        .title("AI Editor Agent API")
                        .version("1.0.0")
                        .description("""
                            # AI Text Editor Agent API
                            
                            This API provides AI-powered document editing capabilities using ReAct and Planning agent patterns.
                            
                            ## Features
                            - **ReAct Agent**: Reasoning + Acting pattern for document editing
                            - **Planning Agent**: Structured planning pattern for complex editing tasks
                            - **Knowledge Base**: Personal knowledge upload endpoints for RAG workflows
                            - **Real-time Updates**: WebSocket support for live progress updates
                            - **Diff Comparison**: Visual diff between original and modified content
                            
                            ## Authentication
                            Currently no authentication required (development mode).
                            
                            ## WebSocket
                            Connect to `/ws/agent` for real-time agent execution updates.
                            """)
                        .contact(new Contact()
                                .name("AI Editor Team")
                                .email("support@aieditor.com")));
    }
}
