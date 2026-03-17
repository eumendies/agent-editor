package com.agent.editor.config;

import com.agent.editor.agent.v2.reflexion.ReflexionActorDefinition;
import com.agent.editor.agent.v2.reflexion.ReflexionCriticDefinition;
import com.agent.editor.agent.v2.trace.TraceCollector;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReflexionAgentConfig {

    @Bean
    public ReflexionActorDefinition reflexionActorDefinition(ChatModel chatModel, TraceCollector traceCollector) {
        return new ReflexionActorDefinition(chatModel, traceCollector);
    }

    @Bean
    public ReflexionCriticDefinition reflexionCriticDefinition(ChatModel chatModel, TraceCollector traceCollector) {
        return new ReflexionCriticDefinition(chatModel, traceCollector);
    }
}
