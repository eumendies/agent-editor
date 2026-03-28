package com.agent.editor.config;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.memory.SessionMemoryStore;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.runtime.PlanningExecutionRuntime;
import com.agent.editor.agent.v2.core.runtime.ToolLoopExecutionRuntime;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.LegacyEventAdapter;
import com.agent.editor.agent.v2.event.WebSocketEventPublisher;
import com.agent.editor.agent.v2.memory.InMemorySessionMemoryStore;
import com.agent.editor.agent.v2.planning.PlanningAgentImpl;
import com.agent.editor.agent.v2.planning.PlanningThenExecutionOrchestrator;
import com.agent.editor.agent.v2.react.ReactAgent;
import com.agent.editor.agent.v2.reflexion.ReflexionActor;
import com.agent.editor.agent.v2.reflexion.ReflexionCritic;
import com.agent.editor.agent.v2.reflexion.ReflexionOrchestrator;
import com.agent.editor.agent.v2.supervisor.SupervisorAgentDefinition;
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
import org.springframework.beans.factory.annotation.Qualifier;

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
    public TaskOrchestrator taskOrchestrator(ToolLoopExecutionRuntime executionRuntime,
                                             PlanningExecutionRuntime planningExecutionRuntime,
                                             EventPublisher eventPublisher,
                                             WorkerRegistry workerRegistry,
                                             PlanningAgentImpl planningAgentImplDefinition,
                                             ReactAgent reactAgentDefinition,
                                             ReflexionActor reflexionActorDefinition,
                                             ReflexionCritic reflexionCriticDefinition,
                                             SupervisorAgentDefinition supervisorAgentDefinition,
                                             SessionMemoryStore sessionMemoryStore) {
        TaskOrchestrator reactOrchestrator = new ReActAgentOrchestrator(executionRuntime, reactAgentDefinition);
        TaskOrchestrator planningOrchestrator = new PlanningThenExecutionOrchestrator(
                planningExecutionRuntime,
                planningAgentImplDefinition,
                executionRuntime,
                reactAgentDefinition
        );
        TaskOrchestrator supervisorOrchestrator = new SupervisorOrchestrator(
                supervisorAgentDefinition,
                workerRegistry,
                executionRuntime,
                eventPublisher
        );
        TaskOrchestrator reflexionOrchestrator = new ReflexionOrchestrator(
                executionRuntime,
                reflexionActorDefinition,
                reflexionCriticDefinition,
                eventPublisher
        );

        return new SessionMemoryTaskOrchestrator(new RoutingTaskOrchestrator(Map.of(
                AgentType.REACT, reactOrchestrator,
                AgentType.PLANNING, planningOrchestrator,
                AgentType.SUPERVISOR, supervisorOrchestrator,
                AgentType.REFLEXION, reflexionOrchestrator
        )), sessionMemoryStore);
    }
}
