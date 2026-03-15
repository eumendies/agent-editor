package com.agent.editor.config;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.planning.PlanningAgentDefinition;
import com.agent.editor.agent.v2.definition.ReactAgentDefinition;
import com.agent.editor.agent.v2.definition.SequentialSupervisorAgentDefinition;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.LegacyEventAdapter;
import com.agent.editor.agent.v2.event.WebSocketEventPublisher;
import com.agent.editor.agent.v2.planning.PlanningThenExecutionOrchestrator;
import com.agent.editor.agent.v2.orchestration.RoutingTaskOrchestrator;
import com.agent.editor.agent.v2.orchestration.SingleAgentOrchestrator;
import com.agent.editor.agent.v2.orchestration.SupervisorOrchestrator;
import com.agent.editor.agent.v2.orchestration.TaskOrchestrator;
import com.agent.editor.agent.v2.orchestration.WorkerDefinition;
import com.agent.editor.agent.v2.orchestration.WorkerRegistry;
import com.agent.editor.agent.v2.core.runtime.DefaultExecutionRuntime;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.trace.DefaultTraceCollector;
import com.agent.editor.agent.v2.trace.InMemoryTraceStore;
import com.agent.editor.agent.v2.trace.TraceCollector;
import com.agent.editor.agent.v2.trace.TraceStore;
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
    public TraceStore traceStore() {
        return new InMemoryTraceStore();
    }

    @Bean
    public TraceCollector traceCollector(TraceStore traceStore) {
        return new DefaultTraceCollector(traceStore);
    }

    @Bean
    public WorkerRegistry workerRegistry(ChatModel chatModel, TraceCollector traceCollector) {
        WorkerRegistry workerRegistry = new WorkerRegistry();
        // 第一版先用异构 worker 池把角色边界和工具边界立住，后面再升级为动态注册或能力发现。
        workerRegistry.register(new WorkerDefinition(
                "analyzer",
                "Analyzer",
                "Inspect the document and identify issues before changes are made.",
                new ReactAgentDefinition(chatModel, traceCollector),
                java.util.List.of("searchContent", "analyzeDocument")
        ));
        workerRegistry.register(new WorkerDefinition(
                "editor",
                "Editor",
                "Apply concrete edits to the document.",
                new ReactAgentDefinition(chatModel, traceCollector),
                java.util.List.of("editDocument", "searchContent")
        ));
        workerRegistry.register(new WorkerDefinition(
                "reviewer",
                "Reviewer",
                "Review the revised document and flag any remaining issues.",
                new ReactAgentDefinition(chatModel, traceCollector),
                java.util.List.of("searchContent", "analyzeDocument")
        ));
        return workerRegistry;
    }

    @Bean
    public ExecutionRuntime executionRuntime(ToolRegistry toolRegistry,
                                             EventPublisher eventPublisher,
                                             TraceCollector traceCollector) {
        return new DefaultExecutionRuntime(
                toolRegistry,
                eventPublisher,
                traceCollector
        );
    }

    @Bean
    public TaskOrchestrator taskOrchestrator(ExecutionRuntime executionRuntime,
                                             EventPublisher eventPublisher,
                                             WorkerRegistry workerRegistry,
                                             ChatModel chatModel,
                                             TraceCollector traceCollector) {
        ReactAgentDefinition reactAgent = new ReactAgentDefinition(chatModel, traceCollector);
        PlanningAgentDefinition planningAgent = new PlanningAgentDefinition(chatModel);
        SequentialSupervisorAgentDefinition supervisorAgent = new SequentialSupervisorAgentDefinition();

        // v2 当前有三条主链：直接执行、先规划后执行、supervisor 多 agent 编排。
        TaskOrchestrator reactOrchestrator = new SingleAgentOrchestrator(executionRuntime, reactAgent);
        TaskOrchestrator planningOrchestrator = new PlanningThenExecutionOrchestrator(
                planningAgent,
                executionRuntime,
                reactAgent,
                eventPublisher,
                traceCollector
        );
        TaskOrchestrator supervisorOrchestrator = new SupervisorOrchestrator(
                supervisorAgent,
                workerRegistry,
                executionRuntime,
                eventPublisher,
                traceCollector
        );

        return new RoutingTaskOrchestrator(Map.of(
                AgentType.REACT, reactOrchestrator,
                AgentType.PLANNING, planningOrchestrator,
                AgentType.SUPERVISOR, supervisorOrchestrator
        ));
    }
}
