package com.agent.editor.config;

import com.agent.editor.agent.v2.reflexion.ReflexionActor;
import com.agent.editor.agent.v2.reflexion.ReflexionActorContextFactory;
import com.agent.editor.agent.v2.reflexion.ReflexionCritic;
import com.agent.editor.agent.v2.reflexion.ReflexionCriticContextFactory;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReflexionAgentConfig {

    @Bean
    public ReflexionActor reflexionActorDefinition(ChatModel chatModel,
                                                   ReflexionActorContextFactory reflexionActorContextFactory) {
        // actor 与 critic 独立装配，避免复用 supervisor reviewer 这类语义上不同的角色定义。
        return new ReflexionActor(chatModel, reflexionActorContextFactory);
    }

    @Bean
    public ReflexionCritic reflexionCriticDefinition(ChatModel chatModel,
                                                     ReflexionCriticContextFactory reflexionCriticContextFactory) {
        return new ReflexionCritic(chatModel, reflexionCriticContextFactory);
    }
}
