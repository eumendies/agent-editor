package com.agent.editor.config;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.SupervisorAgent;
import com.agent.editor.agent.v2.core.memory.SessionMemoryStore;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.runtime.PlanningExecutionRuntime;
import com.agent.editor.agent.v2.core.runtime.ToolLoopExecutionRuntime;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.LegacyEventAdapter;
import com.agent.editor.agent.v2.event.WebSocketEventPublisher;
import com.agent.editor.agent.v2.memory.InMemorySessionMemoryStore;
import com.agent.editor.agent.v2.planning.PlanningAgentContextFactory;
import com.agent.editor.agent.v2.planning.PlanningAgentImpl;
import com.agent.editor.agent.v2.planning.PlanningThenExecutionOrchestrator;
import com.agent.editor.agent.v2.react.ReactAgent;
import com.agent.editor.agent.v2.react.ReactAgentContextFactory;
import com.agent.editor.agent.v2.reflexion.ReflexionActor;
import com.agent.editor.agent.v2.reflexion.ReflexionActorContextFactory;
import com.agent.editor.agent.v2.reflexion.ReflexionCritic;
import com.agent.editor.agent.v2.reflexion.ReflexionCriticContextFactory;
import com.agent.editor.agent.v2.reflexion.ReflexionOrchestrator;
import com.agent.editor.agent.v2.supervisor.SupervisorContextFactory;
import com.agent.editor.agent.v2.supervisor.SupervisorOrchestrator;
import com.agent.editor.agent.v2.supervisor.worker.WorkerRegistry;
import com.agent.editor.agent.v2.task.RoutingTaskOrchestrator;
import com.agent.editor.agent.v2.react.ReActAgentOrchestrator;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.agent.v2.task.SessionMemoryTaskOrchestrator;
import com.agent.editor.agent.v2.tool.ToolRegistry;
import com.agent.editor.service.TaskQueryService;
import com.agent.editor.websocket.WebSocketService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;

@Configuration
public class TaskOrchestratorConfig {

    @Bean
    public LegacyEventAdapter legacyEventAdapter() {
        return new LegacyEventAdapter();
    }

    @Bean
    public EventPublisher eventPublisher(TaskQueryService taskQueryService,
                                         WebSocketService webSocketService,
                                         LegacyEventAdapter legacyEventAdapter) {
        return new WebSocketEventPublisher(taskQueryService, webSocketService, legacyEventAdapter);
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
    public SessionMemoryStore sessionMemoryStore() {
        return new InMemorySessionMemoryStore();
    }

    @Bean
    public ReactAgentContextFactory reactAgentContextFactory() {
        return new ReactAgentContextFactory();
    }

    @Bean
    public PlanningAgentContextFactory planningAgentContextFactory() {
        return new PlanningAgentContextFactory();
    }

    @Bean
    public ReflexionActorContextFactory reflexionActorContextFactory() {
        return new ReflexionActorContextFactory();
    }

    @Bean
    public ReflexionCriticContextFactory reflexionCriticContextFactory() {
        return new ReflexionCriticContextFactory();
    }

    @Bean
    public SupervisorContextFactory supervisorContextFactory() {
        return new SupervisorContextFactory();
    }

    @Bean
    public TaskOrchestrator taskOrchestrator(ToolLoopExecutionRuntime executionRuntime,
                                             PlanningExecutionRuntime planningExecutionRuntime,
                                             EventPublisher eventPublisher,
                                             WorkerRegistry workerRegistry,
                                             PlanningAgentImpl planningAgentImplDefinition,
                                             ReactAgent reactAgentDefinition,
                                             ReflexionActor reflexionActorDefinition,
                                             ReflexionCritic reflexionCriticDefinition,
                                             SupervisorAgent supervisorAgentDefinition,
                                             SessionMemoryStore sessionMemoryStore,
                                             ReactAgentContextFactory reactAgentContextFactory,
                                             PlanningAgentContextFactory planningAgentContextFactory,
                                             ReflexionActorContextFactory reflexionActorContextFactory,
                                             ReflexionCriticContextFactory reflexionCriticContextFactory,
                                             SupervisorContextFactory supervisorContextFactory) {
        TaskOrchestrator reactOrchestrator = new ReActAgentOrchestrator(executionRuntime, reactAgentDefinition, reactAgentContextFactory);
        TaskOrchestrator planningOrchestrator = new PlanningThenExecutionOrchestrator(
                planningExecutionRuntime,
                planningAgentImplDefinition,
                executionRuntime,
                reactAgentDefinition,
                planningAgentContextFactory
        );
        TaskOrchestrator supervisorOrchestrator = new SupervisorOrchestrator(
                supervisorAgentDefinition,
                workerRegistry,
                executionRuntime,
                eventPublisher,
                supervisorContextFactory
        );
        TaskOrchestrator reflexionOrchestrator = new ReflexionOrchestrator(
                executionRuntime,
                reflexionActorDefinition,
                reflexionCriticDefinition,
                reflexionActorContextFactory,
                reflexionCriticContextFactory
        );

        return new SessionMemoryTaskOrchestrator(new RoutingTaskOrchestrator(Map.of(
                AgentType.REACT, reactOrchestrator,
                AgentType.PLANNING, planningOrchestrator,
                AgentType.SUPERVISOR, supervisorOrchestrator,
                AgentType.REFLEXION, reflexionOrchestrator
        )), sessionMemoryStore);
    }
}
