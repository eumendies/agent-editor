package com.agent.editor.config;

import com.agent.editor.agent.core.agent.AgentType;
import com.agent.editor.agent.core.agent.SupervisorAgent;
import com.agent.editor.agent.core.memory.MemoryCompressor;
import com.agent.editor.agent.core.memory.SessionMemoryStore;
import com.agent.editor.agent.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.core.runtime.PlanningExecutionRuntime;
import com.agent.editor.agent.core.runtime.SupervisorExecutionRuntime;
import com.agent.editor.agent.core.runtime.ToolLoopExecutionRuntime;
import com.agent.editor.agent.event.EventPublisher;
import com.agent.editor.agent.event.WebSocketEventPublisher;
import com.agent.editor.agent.memory.InMemorySessionMemoryStore;
import com.agent.editor.agent.memory.ModelBasedMemoryCompressor;
import com.agent.editor.agent.mapper.ExecutionMemoryChatMessageMapper;
import com.agent.editor.agent.planning.PlanningAgentContextFactory;
import com.agent.editor.agent.planning.PlanningAgentImpl;
import com.agent.editor.agent.planning.PlanningThenExecutionOrchestrator;
import com.agent.editor.agent.react.ReactAgent;
import com.agent.editor.agent.react.ReactAgentContextFactory;
import com.agent.editor.agent.reflexion.ReflexionActor;
import com.agent.editor.agent.reflexion.ReflexionActorContextFactory;
import com.agent.editor.agent.reflexion.ReflexionCritic;
import com.agent.editor.agent.reflexion.ReflexionCriticContextFactory;
import com.agent.editor.agent.reflexion.ReflexionOrchestrator;
import com.agent.editor.agent.supervisor.SupervisorContextFactory;
import com.agent.editor.agent.supervisor.SupervisorOrchestrator;
import com.agent.editor.agent.supervisor.worker.WorkerRegistry;
import com.agent.editor.agent.task.RoutingTaskOrchestrator;
import com.agent.editor.agent.react.ReActAgentOrchestrator;
import com.agent.editor.agent.task.TaskOrchestrator;
import com.agent.editor.agent.task.SessionMemoryTaskOrchestrator;
import com.agent.editor.agent.tool.ToolRegistry;
import com.agent.editor.agent.tool.ExecutionToolAccessPolicy;
import com.agent.editor.agent.mcp.config.McpProperties;
import com.agent.editor.agent.tool.document.DocumentToolAccessPolicy;
import com.agent.editor.agent.tool.memory.MemoryToolAccessPolicy;
import com.agent.editor.service.StructuredDocumentService;
import com.agent.editor.service.TaskQueryService;
import com.agent.editor.websocket.WebSocketService;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;

@Configuration
@EnableConfigurationProperties({
        MemoryCompressionProperties.class,
        DocumentToolModeProperties.class,
        McpProperties.class
})
public class TaskOrchestratorConfig {

    @Bean
    public EventPublisher eventPublisher(TaskQueryService taskQueryService,
                                         WebSocketService webSocketService) {
        return new WebSocketEventPublisher(taskQueryService, webSocketService);
    }

    @Bean
    public ToolLoopExecutionRuntime executionRuntime(ToolRegistry toolRegistry,
                                             EventPublisher eventPublisher) {
        return new ToolLoopExecutionRuntime(toolRegistry, eventPublisher);
    }

    @Bean
    public PlanningExecutionRuntime planningExecutionRuntime(EventPublisher eventPublisher) {
        return new PlanningExecutionRuntime(eventPublisher);
    }

    @Bean
    public SupervisorExecutionRuntime supervisorExecutionRuntime(EventPublisher eventPublisher) {
        return new SupervisorExecutionRuntime(eventPublisher);
    }

    @Bean
    public SessionMemoryStore sessionMemoryStore() {
        return new InMemorySessionMemoryStore();
    }

    @Bean
    public MemoryCompressor memoryCompressor(ChatModel chatModel,
                                             MemoryCompressionProperties memoryCompressionProperties) {
        return new ModelBasedMemoryCompressor(chatModel, memoryCompressionProperties);
    }

    @Bean
    public ExecutionMemoryChatMessageMapper executionMemoryChatMessageMapper() {
        return new ExecutionMemoryChatMessageMapper();
    }

    @Bean
    public ReactAgentContextFactory reactAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
                                                             MemoryCompressor memoryCompressor,
                                                             StructuredDocumentService structuredDocumentService) {
        return new ReactAgentContextFactory(memoryChatMessageMapper, memoryCompressor, structuredDocumentService);
    }

    @Bean
    public PlanningAgentContextFactory planningAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
                                                                   MemoryCompressor memoryCompressor) {
        return new PlanningAgentContextFactory(memoryChatMessageMapper, memoryCompressor);
    }

    @Bean
    public ReflexionActorContextFactory reflexionActorContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
                                                                     MemoryCompressor memoryCompressor,
                                                                     StructuredDocumentService structuredDocumentService) {
        return new ReflexionActorContextFactory(memoryChatMessageMapper, memoryCompressor, structuredDocumentService);
    }

    @Bean
    public ReflexionCriticContextFactory reflexionCriticContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
                                                                       MemoryCompressor memoryCompressor,
                                                                       StructuredDocumentService structuredDocumentService) {
        return new ReflexionCriticContextFactory(memoryChatMessageMapper, memoryCompressor, structuredDocumentService);
    }

    @Bean
    public SupervisorContextFactory supervisorContextFactory(MemoryCompressor memoryCompressor,
                                                             StructuredDocumentService structuredDocumentService) {
        return new SupervisorContextFactory(memoryCompressor, structuredDocumentService);
    }

    @Bean
    public DocumentToolAccessPolicy documentToolAccessPolicy(StructuredDocumentService structuredDocumentService,
                                                             DocumentToolModeProperties documentToolModeProperties) {
        return new DocumentToolAccessPolicy(structuredDocumentService, documentToolModeProperties);
    }

    @Bean
    public MemoryToolAccessPolicy memoryToolAccessPolicy() {
        return new MemoryToolAccessPolicy();
    }

    @Bean
    public ExecutionToolAccessPolicy executionToolAccessPolicy(DocumentToolAccessPolicy documentToolAccessPolicy,
                                                               MemoryToolAccessPolicy memoryToolAccessPolicy) {
        return new ExecutionToolAccessPolicy(documentToolAccessPolicy, memoryToolAccessPolicy);
    }

    @Bean
    public TaskOrchestrator taskOrchestrator(ToolLoopExecutionRuntime executionRuntime,
                                             PlanningExecutionRuntime planningExecutionRuntime,
                                             SupervisorExecutionRuntime supervisorExecutionRuntime,
                                             EventPublisher eventPublisher,
                                             WorkerRegistry workerRegistry,
                                             PlanningAgentImpl planningAgentImplDefinition,
                                             ReactAgent reactAgentDefinition,
                                             ReflexionActor reflexionActorDefinition,
                                             ReflexionCritic reflexionCriticDefinition,
                                             SupervisorAgent supervisorAgentDefinition,
                                             MemoryCompressor memoryCompressor,
                                             SessionMemoryStore sessionMemoryStore,
                                             ReactAgentContextFactory reactAgentContextFactory,
                                             PlanningAgentContextFactory planningAgentContextFactory,
                                             ReflexionActorContextFactory reflexionActorContextFactory,
                                             ReflexionCriticContextFactory reflexionCriticContextFactory,
                                             SupervisorContextFactory supervisorContextFactory,
                                             ExecutionToolAccessPolicy executionToolAccessPolicy,
                                             DocumentToolAccessPolicy documentToolAccessPolicy) {
        TaskOrchestrator reactOrchestrator = new ReActAgentOrchestrator(
                executionRuntime,
                reactAgentDefinition,
                reactAgentContextFactory,
                executionToolAccessPolicy
        );
        TaskOrchestrator planningOrchestrator = new PlanningThenExecutionOrchestrator(
                planningExecutionRuntime,
                planningAgentImplDefinition,
                executionRuntime,
                reactAgentDefinition,
                planningAgentContextFactory,
                executionToolAccessPolicy
        );
        TaskOrchestrator supervisorOrchestrator = new SupervisorOrchestrator(
                supervisorAgentDefinition,
                supervisorExecutionRuntime,
                workerRegistry,
                executionRuntime,
                eventPublisher,
                supervisorContextFactory,
                executionToolAccessPolicy
        );
        TaskOrchestrator reflexionOrchestrator = new ReflexionOrchestrator(
                executionRuntime,
                reflexionActorDefinition,
                reflexionCriticDefinition,
                reflexionActorContextFactory,
                reflexionCriticContextFactory,
                executionToolAccessPolicy,
                documentToolAccessPolicy
        );

        return new SessionMemoryTaskOrchestrator(new RoutingTaskOrchestrator(Map.of(
                AgentType.REACT, reactOrchestrator,
                AgentType.PLANNING, planningOrchestrator,
                AgentType.SUPERVISOR, supervisorOrchestrator,
                AgentType.REFLEXION, reflexionOrchestrator
        )), sessionMemoryStore);
    }
}
