package com.agent.editor.config;

import com.agent.editor.agent.v2.planning.PlanningAgentDefinition;
import com.agent.editor.agent.v2.planning.PlanningAiService;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlanningAgentConfig {

    @Bean
    public PlanningAgentDefinition planningAgentDefinition(ChatModel chatModel) {
        PlanningAiService planningAiService = AiServices.builder(PlanningAiService.class)
                .chatModel(chatModel)
                .build();
        return new PlanningAgentDefinition(planningAiService);
    }
}
