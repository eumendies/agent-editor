package com.agent.editor.config;

import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.reflexion.ReflexionActorDefinition;
import com.agent.editor.agent.v2.reflexion.ReflexionCriticDefinition;
import com.agent.editor.agent.v2.supervisor.HybridSupervisorAgentDefinition;
import com.agent.editor.agent.v2.supervisor.SupervisorAgentDefinition;
import com.agent.editor.agent.v2.supervisor.WorkerDefinition;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.agent.v2.supervisor.WorkerRegistry;
import com.agent.editor.agent.v2.trace.TraceCollector;
import com.agent.editor.agent.v2.trace.TraceStore;
import com.agent.editor.agent.v2.tool.ToolRegistry;
import com.agent.editor.model.RetrievedKnowledgeChunk;
import com.agent.editor.service.KnowledgeRetrievalService;
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
import static org.mockito.Mockito.when;

class AgentV2ConfigurationSplitTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    ToolConfig.class,
                    TraceConfig.class,
                    ReactAgentConfig.class,
                    PlanningAgentConfig.class,
                    SupervisorAgentConfig.class,
                    ReflexionAgentConfig.class,
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
            assertThat(context).hasSingleBean(ReflexionActorDefinition.class);
            assertThat(context).hasSingleBean(ReflexionCriticDefinition.class);
            assertThat(context.containsBean("agentV2Config")).isFalse();
        });
    }

    @Test
    void shouldRegisterWorkersWithExpectedCapabilities() {
        contextRunner.run(context -> {
            WorkerRegistry workerRegistry = context.getBean(WorkerRegistry.class);

            assertThat(workerRegistry.all())
                    .extracting(WorkerDefinition::workerId)
                    .containsExactly("analyzer", "editor", "reviewer");
            assertThat(workerRegistry.all())
                    .extracting(WorkerDefinition::capabilities)
                    .allSatisfy(capabilities -> assertThat(capabilities).isNotEmpty());
            assertThat(workerRegistry.get("analyzer").capabilities()).containsExactly("analyze");
            assertThat(workerRegistry.get("editor").capabilities()).containsExactly("edit", "draft");
            assertThat(workerRegistry.get("editor").description()).contains("writing from scratch");
            assertThat(workerRegistry.get("reviewer").capabilities()).containsExactly("review");
        });
    }

    @Test
    void shouldWireHybridSupervisorAsDefaultSupervisorBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SupervisorAgentDefinition.class);
            assertThat(context.getBean(SupervisorAgentDefinition.class))
                    .isInstanceOf(HybridSupervisorAgentDefinition.class);
            assertThat(context).hasSingleBean(TaskOrchestrator.class);
        });
    }

    @Test
    void shouldWireReflexionBeansWithoutReusingSupervisorReviewer() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ReflexionActorDefinition.class);
            assertThat(context).hasSingleBean(ReflexionCriticDefinition.class);
            assertThat(context.getBean(WorkerRegistry.class).all())
                    .extracting(WorkerDefinition::workerId)
                    .doesNotContain("reflexion-critic");
        });
    }

    @Test
    void shouldRegisterRetrieveKnowledgeToolWhenRetrievalServiceExists() {
        contextRunner.withBean(KnowledgeRetrievalService.class, () -> {
            KnowledgeRetrievalService service = mock(KnowledgeRetrievalService.class);
            when(service.retrieve("Spring", null, null))
                    .thenReturn(java.util.List.of(new RetrievedKnowledgeChunk(
                            "doc-1",
                            "resume.md",
                            0,
                            "项目经历",
                            "Spring Boot 项目经验",
                            0.9
                    )));
            return service;
        }).run(context -> {
            ToolRegistry toolRegistry = context.getBean(ToolRegistry.class);

            assertThat(toolRegistry.get("retrieveKnowledge")).isNotNull();
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
