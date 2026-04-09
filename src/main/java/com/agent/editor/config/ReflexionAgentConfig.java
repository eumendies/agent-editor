package com.agent.editor.config;

import com.agent.editor.agent.reflexion.ReflexionActor;
import com.agent.editor.agent.reflexion.ReflexionActorContextFactory;
import com.agent.editor.agent.reflexion.ReflexionCritic;
import com.agent.editor.agent.reflexion.ReflexionCriticContextFactory;
import com.agent.editor.agent.model.StreamingLLMInvoker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReflexionAgentConfig {

    @Bean
    public ReflexionActor reflexionActorDefinition(StreamingLLMInvoker streamingLLMInvoker,
                                                   ReflexionActorContextFactory reflexionActorContextFactory) {
        // actor 与 critic 独立装配，避免复用 supervisor reviewer 这类语义上不同的角色定义。
        return ReflexionActor.streaming(streamingLLMInvoker, reflexionActorContextFactory);
    }

    @Bean
    public ReflexionCritic reflexionCriticDefinition(StreamingLLMInvoker streamingLLMInvoker,
                                                     ReflexionCriticContextFactory reflexionCriticContextFactory) {
        return ReflexionCritic.streaming(streamingLLMInvoker, reflexionCriticContextFactory);
    }
}
