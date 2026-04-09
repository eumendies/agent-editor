package com.agent.editor.config;

import com.agent.editor.agent.react.ReactAgent;
import com.agent.editor.agent.react.ReactAgentContextFactory;
import com.agent.editor.agent.model.StreamingLLMInvoker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReactAgentConfig {

    @Bean
    public ReactAgent reactAgentDefinition(StreamingLLMInvoker streamingLLMInvoker,
                                           ReactAgentContextFactory reactAgentContextFactory) {
        return ReactAgent.streaming(streamingLLMInvoker, reactAgentContextFactory);
    }
}
