package com.agent.editor.config;

import com.agent.editor.agent.v2.react.ReactAgent;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReactAgentConfig {

    @Bean
    public ReactAgent reactAgentDefinition(ChatModel chatModel) {
        return new ReactAgent(chatModel);
    }
}
