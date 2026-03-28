package com.agent.editor.config;

import com.agent.editor.agent.v2.reflexion.ReflexionActor;
import com.agent.editor.agent.v2.reflexion.ReflexionCritic;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReflexionAgentConfig {

    @Bean
    public ReflexionActor reflexionActorDefinition(ChatModel chatModel) {
        // actor 与 critic 独立装配，避免复用 supervisor reviewer 这类语义上不同的角色定义。
        return new ReflexionActor(chatModel);
    }

    @Bean
    public ReflexionCritic reflexionCriticDefinition(ChatModel chatModel) {
        return new ReflexionCritic(chatModel);
    }
}
