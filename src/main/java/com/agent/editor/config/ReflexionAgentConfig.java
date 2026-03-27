package com.agent.editor.config;

import com.agent.editor.agent.v2.reflexion.ReflexionActorDefinition;
import com.agent.editor.agent.v2.reflexion.ReflexionCriticDefinition;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReflexionAgentConfig {

    @Bean
    public ReflexionActorDefinition reflexionActorDefinition(ChatModel chatModel) {
        // actor 与 critic 独立装配，避免复用 supervisor reviewer 这类语义上不同的角色定义。
        return new ReflexionActorDefinition(chatModel);
    }

    @Bean
    public ReflexionCriticDefinition reflexionCriticDefinition(ChatModel chatModel) {
        return new ReflexionCriticDefinition(chatModel);
    }
}
