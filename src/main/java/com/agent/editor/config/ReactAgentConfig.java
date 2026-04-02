package com.agent.editor.config;

import com.agent.editor.agent.v2.react.ReactAgent;
import com.agent.editor.agent.v2.react.ReactAgentContextFactory;
import com.agent.editor.agent.v2.model.StreamingLLMInvoker;
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
