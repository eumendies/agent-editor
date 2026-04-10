package com.agent.editor.agent.planning;

import com.agent.editor.agent.core.agent.Agent;
import com.agent.editor.agent.core.agent.AgentType;
import com.agent.editor.agent.core.agent.PlanResult;
import com.agent.editor.agent.core.agent.ToolLoopAgent;
import com.agent.editor.agent.core.agent.ToolLoopDecision;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.core.state.*;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.runtime.ExecutionRequest;
import com.agent.editor.agent.core.runtime.ExecutionResult;
import com.agent.editor.agent.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.task.TaskRequest;
import com.agent.editor.agent.task.TaskResult;
import com.agent.editor.agent.support.NoOpMemoryCompressors;
import com.agent.editor.agent.tool.ExecutionToolAccessPolicy;
import com.agent.editor.agent.tool.document.DocumentToolAccessPolicy;
import com.agent.editor.agent.tool.document.DocumentToolNames;
import com.agent.editor.agent.tool.memory.MemoryToolAccessPolicy;
import com.agent.editor.agent.tool.memory.MemoryToolNames;
import com.agent.editor.config.DocumentToolModeProperties;
import com.agent.editor.service.StructuredDocumentService;
import com.agent.editor.agent.trace.InMemoryTraceStore;
import com.agent.editor.agent.trace.TraceStore;
import com.agent.editor.utils.rag.markdown.MarkdownSectionTreeBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningThenExecutionOrchestratorTest {

    @Test
    void shouldCreatePlanThroughPlanningRuntimeBeforeExecutingSteps() {
        RecordingPlanningRuntime planningRuntime = new RecordingPlanningRuntime(plan("Add outline", "Refine tone"));
        RecordingExecutionRuntime executionRuntime = new RecordingExecutionRuntime();
        TraceStore traceStore = new InMemoryTraceStore();
        PlanningThenExecutionOrchestrator orchestrator = new PlanningThenExecutionOrchestrator(
                planningRuntime,
                new FailIfCalledPlanningAgentImpl(),
                executionRuntime,
                new CompletingExecutionAgent(),
                com.agent.editor.testsupport.AgentTestFixtures.planningAgentContextFactory(NoOpMemoryCompressors.noop()),
                executionToolAccessPolicy(100)
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-1",
                "session-1",
                AgentType.PLANNING,
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve document",
                5
        ));

        assertEquals(TaskStatus.COMPLETED, result.getStatus());
        assertEquals("body -> Add outline -> Refine tone", result.getFinalContent());
        assertEquals(1, planningRuntime.requests().size());
        assertEquals("Improve document", planningRuntime.requests().get(0).getInstruction());
        assertEquals(List.of("Add outline", "Refine tone"), executionRuntime.instructions());
        assertEquals("body", executionRuntime.requests().get(0).getDocument().getContent());
        assertEquals("body -> Add outline", executionRuntime.requests().get(1).getDocument().getContent());
        assertTrue(traceStore.getByTaskId("task-1").isEmpty());
    }

    @Test
    void shouldReuseExecutionStateAcrossPlanSteps() {
        RecordingPlanningRuntime planningRuntime = new RecordingPlanningRuntime(plan("Add outline", "Refine tone"));
        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        PlanningThenExecutionOrchestrator orchestrator = new PlanningThenExecutionOrchestrator(
                planningRuntime,
                new StaticPlanningAgentImpl(plan("unused")),
                runtime,
                new CompletingExecutionAgent(),
                com.agent.editor.testsupport.AgentTestFixtures.planningAgentContextFactory(NoOpMemoryCompressors.noop()),
                executionToolAccessPolicy(100)
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-2",
                "session-2",
                AgentType.PLANNING,
                new DocumentSnapshot("doc-2", "Title", "body"),
                "Improve document",
                5,
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("previous turn")
                ))
        ));

        assertEquals(TaskStatus.COMPLETED, result.getStatus());
        assertEquals(2, runtime.states().size());
        assertEquals("body", runtime.states().get(0).getCurrentContent());
        assertEquals("body -> Add outline", runtime.states().get(1).getCurrentContent());
        ChatTranscriptMemory firstStepMemory = (ChatTranscriptMemory) runtime.states().get(0).getMemory();
        assertTrue(firstStepMemory.getMessages().stream().anyMatch(message -> "previous turn".equals(message.getText())));
        assertFalse(firstStepMemory.getMessages().stream().anyMatch(message -> "Improve document".equals(message.getText())));
        assertTrue(firstStepMemory.getMessages().stream().anyMatch(message ->
                message instanceof ChatMessage.UserChatMessage userMessage
                        && "Plan step 1: Add outline".equals(userMessage.getText())
        ));
        ChatTranscriptMemory secondStepMemory = (ChatTranscriptMemory) runtime.states().get(1).getMemory();
        assertTrue(secondStepMemory.getMessages().stream().anyMatch(message ->
                message instanceof ChatMessage.UserChatMessage userMessage
                        && "Plan step 1: Add outline".equals(userMessage.getText())
        ));
        assertTrue(secondStepMemory.getMessages().stream().anyMatch(message ->
                message instanceof ChatMessage.AiChatMessage aiMessage
                        && "Step result: completed Add outline".equals(aiMessage.getText())
        ));
        assertTrue(secondStepMemory.getMessages().stream().anyMatch(message ->
                message instanceof ChatMessage.UserChatMessage userMessage
                        && "Plan step 2: Refine tone".equals(userMessage.getText())
        ));
        assertFalse(secondStepMemory.getMessages().stream().anyMatch(ChatMessage.ToolExecutionResultChatMessage.class::isInstance));
        assertFalse(secondStepMemory.getMessages().stream().anyMatch(ChatMessage.AiToolCallChatMessage.class::isInstance));
    }

    @Test
    void shouldUseIncrementalDocumentToolsForLongPlanningExecutionSteps() {
        RecordingPlanningRuntime planningRuntime = new RecordingPlanningRuntime(plan("Add outline"));
        RecordingExecutionRuntime executionRuntime = new RecordingExecutionRuntime();
        PlanningThenExecutionOrchestrator orchestrator = new PlanningThenExecutionOrchestrator(
                planningRuntime,
                new StaticPlanningAgentImpl(plan("unused")),
                executionRuntime,
                new CompletingExecutionAgent(),
                com.agent.editor.testsupport.AgentTestFixtures.planningAgentContextFactory(NoOpMemoryCompressors.noop()),
                executionToolAccessPolicy(10)
        );

        orchestrator.execute(new TaskRequest(
                "task-3",
                "session-3",
                AgentType.PLANNING,
                new DocumentSnapshot("doc-3", "Title", "x".repeat(80)),
                "Improve document",
                5
        ));

        assertEquals(List.of(
                DocumentToolNames.READ_DOCUMENT_NODE,
                DocumentToolNames.PATCH_DOCUMENT_NODE,
                DocumentToolNames.SEARCH_CONTENT,
                MemoryToolNames.SEARCH_MEMORY,
                MemoryToolNames.UPSERT_MEMORY
        ), executionRuntime.requests().get(0).getAllowedTools());
    }

    private DocumentToolAccessPolicy documentToolAccessPolicy(int threshold) {
        return new DocumentToolAccessPolicy(
                new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 4_000, 1_200),
                com.agent.editor.testsupport.ConfigurationTestFixtures.documentToolModeProperties(threshold)
        );
    }

    private ExecutionToolAccessPolicy executionToolAccessPolicy(int threshold) {
        return new ExecutionToolAccessPolicy(
                documentToolAccessPolicy(threshold),
                new MemoryToolAccessPolicy()
        );
    }

    private static PlanResult plan(String... instructions) {
        return new PlanResult().withInstructions(List.of(instructions));
    }

    private static final class RecordingPlanningRuntime implements ExecutionRuntime {

        private final List<ExecutionRequest> requests = new ArrayList<>();
        private final PlanResult planResult;

        private RecordingPlanningRuntime(PlanResult planResult) {
            this.planResult = planResult;
        }

        @Override
        public ExecutionResult run(Agent agent, ExecutionRequest request) {
            requests.add(request);
            return new ExecutionResult(planResult, "plan created", request.getDocument().getContent());
        }

        @Override
        public ExecutionResult run(Agent agent, ExecutionRequest request, AgentRunContext initialContext) {
            requests.add(request);
            return new ExecutionResult(
                    planResult,
                    "plan created",
                    request.getDocument().getContent(),
                    initialContext.markCompleted()
            );
        }

        private List<ExecutionRequest> requests() {
            return requests;
        }
    }

    private static final class StaticPlanningAgentImpl extends PlanningAgentImpl {

        private final PlanResult planResult;

        private StaticPlanningAgentImpl(PlanResult planResult) {
            super(null, com.agent.editor.testsupport.AgentTestFixtures.structuredDocumentService());
            this.planResult = planResult;
        }

        @Override
        public PlanResult createPlan(AgentRunContext context) {
            return planResult;
        }
    }

    private static final class FailIfCalledPlanningAgentImpl extends PlanningAgentImpl {

        private FailIfCalledPlanningAgentImpl() {
            super(null, com.agent.editor.testsupport.AgentTestFixtures.structuredDocumentService());
        }

        @Override
        public PlanResult createPlan(AgentRunContext context) {
            throw new AssertionError("planner should be invoked through planning runtime");
        }
    }

    private static final class CompletingExecutionAgent implements ToolLoopAgent {

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public ToolLoopDecision decide(AgentRunContext context) {
            return new ToolLoopDecision.Complete(context.getRequest().getInstruction(), "done");
        }
    }

    private static final class RecordingExecutionRuntime implements ExecutionRuntime {

        private final List<ExecutionRequest> requests = new ArrayList<>();
        private final List<AgentRunContext> states = new ArrayList<>();

        @Override
        public ExecutionResult run(Agent agent, ExecutionRequest request) {
            requests.add(request);
            return new ExecutionResult(request.getInstruction(), request.getDocument().getContent() + " -> " + request.getInstruction());
        }

        @Override
        public ExecutionResult run(Agent definition, ExecutionRequest request, AgentRunContext initialState) {
            requests.add(request);
            states.add(initialState);
            String updatedContent = initialState.getCurrentContent() + " -> " + request.getInstruction();
            return new ExecutionResult(
                    request.getInstruction(),
                    "completed " + request.getInstruction(),
                    updatedContent,
                    new AgentRunContext(
                            request,
                            initialState.getIteration() + 1,
                            updatedContent,
                            new ChatTranscriptMemory(List.of(
                                    new ChatMessage.AiToolCallChatMessage(
                                            "need tool",
                                            List.of()
                                    ),
                                    new ChatMessage.ToolExecutionResultChatMessage(
                                            "tool-1",
                                            "searchContent",
                                            "{\"query\":\"heading\"}",
                                            "tool result"
                                    ),
                                    new ChatMessage.AiChatMessage("completed " + request.getInstruction())
                            )),
                            ExecutionStage.COMPLETED,
                            null,
                            List.of()
                    )
            );
        }

        private List<String> instructions() {
            return requests.stream().map(ExecutionRequest::getInstruction).toList();
        }

        private List<ExecutionRequest> requests() {
            return requests;
        }

        private List<AgentRunContext> states() {
            return states;
        }
    }
}
