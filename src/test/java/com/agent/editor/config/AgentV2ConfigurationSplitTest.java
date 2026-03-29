package com.agent.editor.config;

import com.agent.editor.agent.v2.core.agent.SupervisorAgent;
import com.agent.editor.agent.v2.core.context.SupervisorContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.runtime.PlanningExecutionRuntime;
import com.agent.editor.agent.v2.core.runtime.SupervisorExecutionRuntime;
import com.agent.editor.agent.v2.core.runtime.ToolLoopExecutionRuntime;
import com.agent.editor.agent.v2.reflexion.ReflexionActor;
import com.agent.editor.agent.v2.reflexion.ReflexionCritic;
import com.agent.editor.agent.v2.supervisor.SupervisorContextFactory;
import com.agent.editor.agent.v2.supervisor.routing.HybridSupervisorAgent;
import com.agent.editor.agent.v2.supervisor.worker.*;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.agent.v2.supervisor.worker.EvidenceReviewerAgent;
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
import org.springframework.test.util.ReflectionTestUtils;

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
            assertThat(context).hasSingleBean(ToolLoopExecutionRuntime.class);
            assertThat(context).hasSingleBean(PlanningExecutionRuntime.class);
            assertThat(context).hasSingleBean(SupervisorExecutionRuntime.class);
            assertThat(context).getBeans(ExecutionRuntime.class).hasSize(3);
            assertThat(context).hasSingleBean(TaskOrchestrator.class);
            assertThat(context).hasSingleBean(ReflexionActor.class);
            assertThat(context).hasSingleBean(ReflexionCritic.class);
            assertThat(context).hasSingleBean(SupervisorContextFactory.class);
            assertThat(context.containsBean("agentV2Config")).isFalse();
        });
    }

    @Test
    void shouldRegisterWorkersWithExpectedCapabilities() {
        contextRunner.run(context -> {
            WorkerRegistry workerRegistry = context.getBean(WorkerRegistry.class);

            assertThat(workerRegistry.all())
                    .extracting(SupervisorContext.WorkerDefinition::getWorkerId)
                    .containsExactly("researcher", "writer", "reviewer");
            assertThat(workerRegistry.all())
                    .extracting(SupervisorContext.WorkerDefinition::getCapabilities)
                    .allSatisfy(capabilities -> assertThat(capabilities).isNotEmpty());
            assertThat(workerRegistry.get("researcher").getCapabilities()).containsExactly("research");
            assertThat(workerRegistry.get("writer").getCapabilities()).containsExactly("write", "edit");
            assertThat(workerRegistry.get("writer").getDescription()).contains("grounded");
            assertThat(workerRegistry.get("reviewer").getCapabilities()).containsExactly("review");
            assertThat(workerRegistry.get("researcher").getAgent()).isInstanceOf(ResearcherAgent.class);
            assertThat(workerRegistry.get("writer").getAgent()).isInstanceOf(GroundedWriterAgent.class);
            assertThat(workerRegistry.get("reviewer").getAgent()).isInstanceOf(EvidenceReviewerAgent.class);
        });
    }

    @Test
    void shouldWireHybridSupervisorAsDefaultSupervisorBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SupervisorAgent.class);
            assertThat(context.getBean(SupervisorAgent.class))
                    .isInstanceOf(HybridSupervisorAgent.class);
            assertThat(ReflectionTestUtils.getField(context.getBean(SupervisorAgent.class), "contextFactory"))
                    .isSameAs(context.getBean(SupervisorContextFactory.class));
            assertThat(context).hasSingleBean(TaskOrchestrator.class);
        });
    }

    @Test
    void shouldWireReflexionBeansWithoutReusingSupervisorReviewer() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ReflexionActor.class);
            assertThat(context).hasSingleBean(ReflexionCritic.class);
            assertThat(context.getBean(WorkerRegistry.class).all())
                    .extracting(SupervisorContext.WorkerDefinition::getWorkerId)
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

    @Test
    void shouldRegisterAppendAndSnapshotDocumentTools() {
        contextRunner.run(context -> {
            ToolRegistry toolRegistry = context.getBean(ToolRegistry.class);

            assertThat(toolRegistry.get("appendToDocument")).isNotNull();
            assertThat(toolRegistry.get("getDocumentSnapshot")).isNotNull();
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
