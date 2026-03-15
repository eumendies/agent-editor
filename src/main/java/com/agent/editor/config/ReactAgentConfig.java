package com.agent.editor.config;

import com.agent.editor.agent.v2.react.ReactAgentDefinition;
import com.agent.editor.agent.v2.trace.TraceCollector;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReactAgentConfig {

    @Bean
    public ReactAgentDefinition reactAgentDefinition(ChatModel chatModel, TraceCollector traceCollector) {
        return new ReactAgentDefinition(chatModel, traceCollector);
    }
}
