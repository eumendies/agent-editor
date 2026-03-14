package com.agent.editor.config;

import com.agent.editor.agent.v2.definition.ReactAgentDefinition;
import com.agent.editor.agent.v2.orchestration.SingleAgentOrchestrator;
import com.agent.editor.agent.v2.orchestration.TaskOrchestrator;
import com.agent.editor.agent.v2.runtime.DefaultExecutionRuntime;
import com.agent.editor.agent.v2.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.tool.ToolRegistry;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentV2Config {

    @Bean
    public ToolRegistry toolRegistry() {
        return new ToolRegistry();
    }

    @Bean
    public ExecutionRuntime executionRuntime(ToolRegistry toolRegistry) {
        return new DefaultExecutionRuntime(toolRegistry, event -> {});
    }

    @Bean
    public TaskOrchestrator taskOrchestrator(ExecutionRuntime executionRuntime, ChatModel chatModel) {
        return new SingleAgentOrchestrator(executionRuntime, new ReactAgentDefinition(chatModel));
    }
}
