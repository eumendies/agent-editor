package com.agent.editor.config;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.memory.SessionMemoryStore;
import com.agent.editor.agent.v2.core.runtime.DefaultExecutionRuntime;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.LegacyEventAdapter;
import com.agent.editor.agent.v2.event.WebSocketEventPublisher;
import com.agent.editor.agent.v2.memory.InMemorySessionMemoryStore;
import com.agent.editor.agent.v2.planning.PlanningAgentDefinition;
import com.agent.editor.agent.v2.planning.PlanningThenExecutionOrchestrator;
import com.agent.editor.agent.v2.react.ReactAgentDefinition;
import com.agent.editor.agent.v2.reflexion.ReflexionActorDefinition;
import com.agent.editor.agent.v2.reflexion.ReflexionCriticDefinition;
import com.agent.editor.agent.v2.reflexion.ReflexionOrchestrator;
import com.agent.editor.agent.v2.supervisor.SupervisorAgentDefinition;
import com.agent.editor.agent.v2.supervisor.SupervisorOrchestrator;
import com.agent.editor.agent.v2.supervisor.worker.WorkerRegistry;
import com.agent.editor.agent.v2.task.RoutingTaskOrchestrator;
import com.agent.editor.agent.v2.react.ReActAgentOrchestrator;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.agent.v2.task.SessionMemoryTaskOrchestrator;
import com.agent.editor.agent.v2.trace.TraceCollector;
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
    public ExecutionRuntime executionRuntime(ToolRegistry toolRegistry,
                                             EventPublisher eventPublisher,
                                             TraceCollector traceCollector) {
        return new DefaultExecutionRuntime(toolRegistry, eventPublisher, traceCollector);
    }

    @Bean
    public SessionMemoryStore sessionMemoryStore() {
        return new InMemorySessionMemoryStore();
    }

    @Bean
    public TaskOrchestrator taskOrchestrator(ExecutionRuntime executionRuntime,
                                             EventPublisher eventPublisher,
                                             WorkerRegistry workerRegistry,
                                             PlanningAgentDefinition planningAgentDefinition,
                                             ReactAgentDefinition reactAgentDefinition,
                                             ReflexionActorDefinition reflexionActorDefinition,
                                             ReflexionCriticDefinition reflexionCriticDefinition,
                                             SupervisorAgentDefinition supervisorAgentDefinition,
                                             TraceCollector traceCollector,
                                             SessionMemoryStore sessionMemoryStore) {
        TaskOrchestrator reactOrchestrator = new ReActAgentOrchestrator(executionRuntime, reactAgentDefinition);
        TaskOrchestrator planningOrchestrator = new PlanningThenExecutionOrchestrator(
                planningAgentDefinition,
                executionRuntime,
                reactAgentDefinition,
                eventPublisher,
                traceCollector
        );
        TaskOrchestrator supervisorOrchestrator = new SupervisorOrchestrator(
                supervisorAgentDefinition,
                workerRegistry,
                executionRuntime,
                eventPublisher,
                traceCollector
        );
        TaskOrchestrator reflexionOrchestrator = new ReflexionOrchestrator(
                executionRuntime,
                reflexionActorDefinition,
                reflexionCriticDefinition,
                eventPublisher,
                traceCollector
        );

        return new SessionMemoryTaskOrchestrator(new RoutingTaskOrchestrator(Map.of(
                AgentType.REACT, reactOrchestrator,
                AgentType.PLANNING, planningOrchestrator,
                AgentType.SUPERVISOR, supervisorOrchestrator,
                AgentType.REFLEXION, reflexionOrchestrator
        )), sessionMemoryStore);
    }
}
