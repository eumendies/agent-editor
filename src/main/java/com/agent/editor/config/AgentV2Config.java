package com.agent.editor.config;

import com.agent.editor.agent.v2.definition.AgentType;
import com.agent.editor.agent.v2.definition.PlanningAgentDefinition;
import com.agent.editor.agent.v2.definition.ReactAgentDefinition;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.LegacyEventAdapter;
import com.agent.editor.agent.v2.event.WebSocketEventPublisher;
import com.agent.editor.agent.v2.orchestration.PlanningThenExecutionOrchestrator;
import com.agent.editor.agent.v2.orchestration.RoutingTaskOrchestrator;
import com.agent.editor.agent.v2.orchestration.SingleAgentOrchestrator;
import com.agent.editor.agent.v2.orchestration.TaskOrchestrator;
import com.agent.editor.agent.v2.runtime.DefaultExecutionRuntime;
import com.agent.editor.agent.v2.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.tool.ToolRegistry;
import com.agent.editor.agent.v2.tool.document.AnalyzeDocumentTool;
import com.agent.editor.agent.v2.tool.document.EditDocumentTool;
import com.agent.editor.agent.v2.tool.document.SearchContentTool;
import com.agent.editor.service.TaskQueryService;
import com.agent.editor.websocket.WebSocketService;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class AgentV2Config {

    @Bean
    public ToolRegistry toolRegistry() {
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new EditDocumentTool());
        toolRegistry.register(new SearchContentTool());
        toolRegistry.register(new AnalyzeDocumentTool());
        return toolRegistry;
    }

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
    public ExecutionRuntime executionRuntime(ToolRegistry toolRegistry, EventPublisher eventPublisher) {
        return new DefaultExecutionRuntime(toolRegistry, eventPublisher);
    }

    @Bean
    public TaskOrchestrator taskOrchestrator(ExecutionRuntime executionRuntime,
                                             EventPublisher eventPublisher,
                                             ChatModel chatModel) {
        ReactAgentDefinition reactAgent = new ReactAgentDefinition(chatModel);
        PlanningAgentDefinition planningAgent = new PlanningAgentDefinition(chatModel);

        TaskOrchestrator reactOrchestrator = new SingleAgentOrchestrator(executionRuntime, reactAgent);
        TaskOrchestrator planningOrchestrator = new PlanningThenExecutionOrchestrator(
                planningAgent,
                executionRuntime,
                reactAgent,
                eventPublisher
        );

        return new RoutingTaskOrchestrator(Map.of(
                AgentType.REACT, reactOrchestrator,
                AgentType.PLANNING, planningOrchestrator
        ));
    }
}
