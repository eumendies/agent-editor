package com.agent.editor.config;

import com.agent.editor.agent.planning.PlanningAgentImpl;
import com.agent.editor.agent.planning.PlanningAiService;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlanningAgentConfig {

    @Bean
    public PlanningAgentImpl planningAgentDefinition(ChatModel chatModel) {
        PlanningAiService planningAiService = AiServices.builder(PlanningAiService.class)
                .chatModel(chatModel)
                .build();
        return new PlanningAgentImpl(planningAiService);
    }
}
