package com.agent.editor.config;

import com.agent.editor.agent.core.agent.SupervisorAgent;
import com.agent.editor.agent.core.context.SupervisorContext;
import com.agent.editor.agent.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.core.runtime.PlanningExecutionRuntime;
import com.agent.editor.agent.core.runtime.SupervisorExecutionRuntime;
import com.agent.editor.agent.core.runtime.ToolLoopExecutionRuntime;
import com.agent.editor.agent.model.StreamingLLMInvoker;
import com.agent.editor.agent.reflexion.ReflexionActor;
import com.agent.editor.agent.reflexion.ReflexionCritic;
import com.agent.editor.agent.supervisor.SupervisorContextFactory;
import com.agent.editor.agent.supervisor.SupervisorWorkerIds;
import com.agent.editor.agent.supervisor.routing.HybridSupervisorAgent;
import com.agent.editor.agent.supervisor.worker.*;
import com.agent.editor.agent.tool.ExecutionToolAccessPolicy;
import com.agent.editor.agent.tool.ExecutionToolAccessRole;
import com.agent.editor.agent.mcp.config.McpProperties;
import com.agent.editor.agent.tool.document.DocumentToolNames;
import com.agent.editor.agent.tool.memory.MemoryToolAccessPolicy;
import com.agent.editor.agent.task.TaskOrchestrator;
import com.agent.editor.agent.supervisor.worker.EvidenceReviewerAgent;
import com.agent.editor.agent.trace.TraceCollector;
import com.agent.editor.agent.trace.TraceStore;
import com.agent.editor.agent.tool.ToolRegistry;
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

class AgentConfigurationSplitTest {

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
    void shouldWireAgentBeansAfterConfigurationSplit() {
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
            assertThat(context).hasSingleBean(ResearcherAgentContextFactory.class);
            assertThat(context).hasSingleBean(GroundedWriterAgentContextFactory.class);
            assertThat(context).hasSingleBean(EvidenceReviewerAgentContextFactory.class);
            assertThat(context).hasSingleBean(MemoryAgentContextFactory.class);
            assertThat(context).hasSingleBean(MemoryCompressionProperties.class);
            assertThat(context).hasSingleBean(DocumentToolModeProperties.class);
            assertThat(context).hasSingleBean(McpProperties.class);
            assertThat(context).hasSingleBean(com.agent.editor.agent.tool.document.DocumentToolAccessPolicy.class);
            assertThat(context).hasSingleBean(MemoryToolAccessPolicy.class);
            assertThat(context).hasSingleBean(ExecutionToolAccessPolicy.class);
            assertThat(context.getBeansOfType(ExecutionToolAccessPolicy.class)).hasSize(1);
            assertThat(context.containsBean("supervisorWorkerToolAccessPolicy")).isFalse();
            assertThat(context.containsBean("agentConfig")).isFalse();
        });
    }

    @Test
    void shouldBindMemoryCompressionPropertiesFromConfiguration() {
        contextRunner.withPropertyValues(
                "agent.memory-compression.trigger-total-tokens=4321",
                "agent.memory-compression.preserve-latest-message-count=5",
                "agent.memory-compression.fallback-max-message-count=77"
        ).run(context -> {
            MemoryCompressionProperties properties = context.getBean(MemoryCompressionProperties.class);

            assertThat(properties.getTriggerTotalTokens()).isEqualTo(4321);
            assertThat(properties.getPreserveLatestMessageCount()).isEqualTo(5);
            assertThat(properties.getFallbackMaxMessageCount()).isEqualTo(77);
        });
    }

    @Test
    void shouldBindDocumentToolModePropertiesFromConfiguration() {
        contextRunner.withPropertyValues(
                "agent.document-tool-mode.long-document-threshold-tokens=4321"
        ).run(context -> {
            DocumentToolModeProperties properties = context.getBean(DocumentToolModeProperties.class);

            assertThat(properties.getLongDocumentThresholdTokens()).isEqualTo(4321);
        });
    }

    @Test
    void shouldRegisterWorkersWithExpectedCapabilities() {
        contextRunner.run(context -> {
            WorkerRegistry workerRegistry = context.getBean(WorkerRegistry.class);

            assertThat(workerRegistry.all())
                    .extracting(SupervisorContext.WorkerDefinition::getWorkerId)
                    .containsExactly(
                            SupervisorWorkerIds.RESEARCHER,
                            SupervisorWorkerIds.WRITER,
                            SupervisorWorkerIds.REVIEWER,
                            SupervisorWorkerIds.MEMORY
                    );
            assertThat(workerRegistry.get(SupervisorWorkerIds.RESEARCHER).getExecutionToolAccessRole())
                    .isEqualTo(ExecutionToolAccessRole.RESEARCH);
            assertThat(workerRegistry.get(SupervisorWorkerIds.WRITER).getExecutionToolAccessRole())
                    .isEqualTo(ExecutionToolAccessRole.MAIN_WRITE);
            assertThat(workerRegistry.get(SupervisorWorkerIds.WRITER).getDescription()).contains("grounded");
            assertThat(workerRegistry.get(SupervisorWorkerIds.REVIEWER).getExecutionToolAccessRole())
                    .isEqualTo(ExecutionToolAccessRole.REVIEW);
            assertThat(workerRegistry.get(SupervisorWorkerIds.MEMORY).getExecutionToolAccessRole())
                    .isEqualTo(ExecutionToolAccessRole.MEMORY);
            assertThat(workerRegistry.get(SupervisorWorkerIds.RESEARCHER).getAgent()).isInstanceOf(ResearcherAgent.class);
            assertThat(workerRegistry.get(SupervisorWorkerIds.WRITER).getAgent()).isInstanceOf(GroundedWriterAgent.class);
            assertThat(workerRegistry.get(SupervisorWorkerIds.REVIEWER).getAgent()).isInstanceOf(EvidenceReviewerAgent.class);
            assertThat(workerRegistry.get(SupervisorWorkerIds.MEMORY).getAgent()).isInstanceOf(MemoryAgent.class);
            assertThat(ReflectionTestUtils.getField(workerRegistry.get(SupervisorWorkerIds.RESEARCHER).getAgent(), "contextFactory"))
                    .isSameAs(context.getBean(ResearcherAgentContextFactory.class));
            assertThat(ReflectionTestUtils.getField(workerRegistry.get(SupervisorWorkerIds.WRITER).getAgent(), "contextFactory"))
                    .isSameAs(context.getBean(GroundedWriterAgentContextFactory.class));
            assertThat(ReflectionTestUtils.getField(workerRegistry.get(SupervisorWorkerIds.REVIEWER).getAgent(), "contextFactory"))
                    .isSameAs(context.getBean(EvidenceReviewerAgentContextFactory.class));
            assertThat(ReflectionTestUtils.getField(workerRegistry.get(SupervisorWorkerIds.MEMORY).getAgent(), "contextFactory"))
                    .isSameAs(context.getBean(MemoryAgentContextFactory.class));
        });
    }

    @Test
    void shouldWireHybridSupervisorAsDefaultSupervisorBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SupervisorAgent.class);
            assertThat(context.getBean(SupervisorAgent.class))
                    .isInstanceOf(HybridSupervisorAgent.class);
            assertThat(ReflectionTestUtils.getField(context.getBean(SupervisorAgent.class), "chatModel"))
                    .isSameAs(context.getBean(ChatModel.class));
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

            assertThat(toolRegistry.get(DocumentToolNames.RETRIEVE_KNOWLEDGE)).isNotNull();
        });
    }

    @Test
    void shouldRegisterAppendAndSnapshotDocumentTools() {
        contextRunner.run(context -> {
            ToolRegistry toolRegistry = context.getBean(ToolRegistry.class);

            assertThat(toolRegistry.get(DocumentToolNames.APPEND_TO_DOCUMENT)).isNotNull();
            assertThat(toolRegistry.get(DocumentToolNames.GET_DOCUMENT_SNAPSHOT)).isNotNull();
        });
    }

    @Test
    void shouldRegisterStructuredDocumentTools() {
        contextRunner.run(context -> {
            ToolRegistry toolRegistry = context.getBean(ToolRegistry.class);

            assertThat(toolRegistry.get(DocumentToolNames.READ_DOCUMENT_NODE)).isNotNull();
            assertThat(toolRegistry.get(DocumentToolNames.PATCH_DOCUMENT_NODE)).isNotNull();
        });
    }

    @Configuration
    static class StubDependencyConfig {

        @Bean
        ChatModel chatModel() {
            return mock(ChatModel.class);
        }

        @Bean
        StreamingLLMInvoker streamingDecisionInvoker() {
            return mock(StreamingLLMInvoker.class);
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
