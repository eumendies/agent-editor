package com.agent.editor.config;

import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.agent.v2.supervisor.WorkerRegistry;
import com.agent.editor.agent.v2.trace.TraceCollector;
import com.agent.editor.agent.v2.trace.TraceStore;
import com.agent.editor.agent.v2.tool.ToolRegistry;
import com.agent.editor.service.TaskQueryService;
import com.agent.editor.websocket.WebSocketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentV2ConfigurationSplitTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    ToolConfig.class,
                    TraceConfig.class,
                    ReactAgentConfig.class,
                    PlanningAgentConfig.class,
                    SupervisorAgentConfig.class,
                    TaskOrchestratorConfig.class,
                    StubDependencyConfig.class
            );

    @Test
    void shouldWireAgentV2BeansAfterConfigurationSplit() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ToolRegistry.class);
            assertThat(context).hasSingleBean(TraceStore.class);
            assertThat(context).hasSingleBean(TraceCollector.class);
            assertThat(context).hasSingleBean(WorkerRegistry.class);
            assertThat(context).hasSingleBean(ExecutionRuntime.class);
            assertThat(context).hasSingleBean(TaskOrchestrator.class);
            assertThat(context.containsBean("agentV2Config")).isFalse();
        });
    }

    @Configuration
    static class StubDependencyConfig {

        @Bean
        ChatModel chatModel() {
            return mock(ChatModel.class);
        }

        @Bean
        TaskQueryService taskQueryService() {
            return new TaskQueryService();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        WebSocketService webSocketService() {
            return new WebSocketService();
        }
    }
}
