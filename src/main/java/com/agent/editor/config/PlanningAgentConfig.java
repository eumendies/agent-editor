package com.agent.editor.config;

import com.agent.editor.agent.v2.planning.PlanningAgentDefinition;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlanningAgentConfig {

    @Bean
    public PlanningAgentDefinition planningAgentDefinition(ChatModel chatModel) {
        return new PlanningAgentDefinition(chatModel);
    }
}
