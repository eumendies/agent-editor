package com.agent.editor.agent.v2.reflexion;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.runtime.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.agent.v2.trace.DefaultTraceCollector;
import com.agent.editor.agent.v2.trace.InMemoryTraceStore;
import com.agent.editor.agent.v2.trace.TraceCategory;
import com.agent.editor.agent.v2.trace.TraceStore;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.agent.v2.task.TaskResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflexionOrchestratorTest {

    @Test
    void shouldReturnActorContentWhenCriticPassesImmediately() {
        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        ReflexionOrchestrator orchestrator = new ReflexionOrchestrator(
                runtime,
                new ActorAgentDefinition(),
                criticWithResponses("""
                        {"verdict":"PASS","feedback":"Looks good","reasoning":"done"}
                        """),
                event -> {},
                new DefaultTraceCollector(new InMemoryTraceStore())
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-reflex-1",
                "session-reflex-1",
                AgentType.REFLEXION,
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve the draft",
                3
        ));

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals("body -> actor-pass-1", result.finalContent());
        assertEquals(1, runtime.actorStates.size());
        assertEquals(1, runtime.criticStates.size());
        assertEquals(List.of("editDocument", "searchContent"), runtime.actorAllowedTools.get(0));
        assertEquals(List.of("searchContent", "analyzeDocument"), runtime.criticAllowedTools.get(0));
    }

    @Test
    void shouldFeedReviseCritiqueIntoNextActorRound() {
        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        ReflexionOrchestrator orchestrator = new ReflexionOrchestrator(
                runtime,
                new ActorAgentDefinition(),
                criticWithResponses(
                        """
                        {"verdict":"REVISE","feedback":"Tighten the introduction","reasoning":"too long"}
                        """,
                        """
                        {"verdict":"PASS","feedback":"Looks good","reasoning":"done"}
                        """
                ),
                event -> {},
                new DefaultTraceCollector(new InMemoryTraceStore())
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-reflex-2",
                "session-reflex-2",
                AgentType.REFLEXION,
                new DocumentSnapshot("doc-2", "Title", "body"),
                "Improve the draft",
                3,
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("previous turn")
                ))
        ));

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals("body -> actor-pass-1 -> actor-pass-2", result.finalContent());
        assertEquals(2, runtime.actorStates.size());
        assertEquals(2, runtime.criticStates.size());
        ChatTranscriptMemory firstActorMemory = (ChatTranscriptMemory) runtime.actorStates.get(0).memory();
        assertTrue(firstActorMemory.messages().stream().anyMatch(message -> "previous turn".equals(message.text())));
        ChatTranscriptMemory secondActorMemory = (ChatTranscriptMemory) runtime.actorStates.get(1).memory();
        assertTrue(secondActorMemory.messages().stream().anyMatch(message ->
                message instanceof ChatMessage.UserChatMessage userMessage
                        && userMessage.text().contains("Tighten the introduction")
        ));
        assertEquals(0, runtime.criticStates.get(0).iteration());
        assertEquals(0, runtime.criticStates.get(1).iteration());
    }

    @Test
    void shouldStopWhenMaxReflectionRoundsReached() {
        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        ReflexionOrchestrator orchestrator = new ReflexionOrchestrator(
                runtime,
                new ActorAgentDefinition(),
                criticWithResponses(
                        """
                        {"verdict":"REVISE","feedback":"Round 1","reasoning":"continue"}
                        """,
                        """
                        {"verdict":"REVISE","feedback":"Round 2","reasoning":"continue"}
                        """
                ),
                event -> {},
                new DefaultTraceCollector(new InMemoryTraceStore())
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-reflex-3",
                "session-reflex-3",
                AgentType.REFLEXION,
                new DocumentSnapshot("doc-3", "Title", "body"),
                "Improve the draft",
                2
        ));

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals("body -> actor-pass-1 -> actor-pass-2", result.finalContent());
        assertEquals(2, runtime.actorStates.size());
        assertEquals(2, runtime.criticStates.size());
    }

    @Test
    void shouldEmitTraceStagesForReviseAndPassFlow() {
        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        TraceStore traceStore = new InMemoryTraceStore();
        ReflexionOrchestrator orchestrator = new ReflexionOrchestrator(
                runtime,
                new ActorAgentDefinition(),
                criticWithResponses(
                        """
                        {"verdict":"REVISE","feedback":"Tighten the introduction","reasoning":"too long"}
                        """,
                        """
                        {"verdict":"PASS","feedback":"Looks good","reasoning":"done"}
                        """
                ),
                event -> {},
                new DefaultTraceCollector(traceStore)
        );

        orchestrator.execute(new TaskRequest(
                "task-reflex-4",
                "session-reflex-4",
                AgentType.REFLEXION,
                new DocumentSnapshot("doc-4", "Title", "body"),
                "Improve the draft",
                3
        ));

        var traces = traceStore.getByTaskId("task-reflex-4");
        assertTrue(traces.stream().anyMatch(trace ->
                trace.category() == TraceCategory.ORCHESTRATION_DECISION
                        && "reflexion.actor.started".equals(trace.stage())
        ));
        assertTrue(traces.stream().anyMatch(trace ->
                trace.category() == TraceCategory.ORCHESTRATION_DECISION
                        && "reflexion.critic.completed".equals(trace.stage())
        ));
        assertTrue(traces.stream().anyMatch(trace ->
                trace.category() == TraceCategory.ORCHESTRATION_DECISION
                        && "reflexion.revise".equals(trace.stage())
        ));
        assertTrue(traces.stream().anyMatch(trace ->
                trace.category() == TraceCategory.ORCHESTRATION_DECISION
                        && "reflexion.pass".equals(trace.stage())
        ));
    }

    private ReflexionCriticDefinition criticWithResponses(String... responses) {
        return new ReflexionCriticDefinition(
                new QueueChatModel(responses),
                new DefaultTraceCollector(new InMemoryTraceStore())
        );
    }

    private static final class ActorAgentDefinition implements AgentDefinition {

        @Override
        public AgentType type() {
            return AgentType.REFLEXION;
        }

        @Override
        public Decision decide(AgentRunContext context) {
            ChatTranscriptMemory transcriptMemory = (ChatTranscriptMemory) context.state().memory();
            long critiqueCount = transcriptMemory.messages().stream()
                    .filter(ChatMessage.UserChatMessage.class::isInstance)
                    .map(ChatMessage.UserChatMessage.class::cast)
                    .filter(message -> message.text().startsWith("Critique round"))
                    .count();
            long round = critiqueCount + 1;
            return new Decision.Complete(context.state().currentContent() + " -> actor-pass-" + round, "actor done");
        }
    }

    private static final class RecordingExecutionRuntime implements ExecutionRuntime {

        private final List<AgentRunContext> actorStates = new ArrayList<>();
        private final List<AgentRunContext> criticStates = new ArrayList<>();
        private final List<List<String>> actorAllowedTools = new ArrayList<>();
        private final List<List<String>> criticAllowedTools = new ArrayList<>();

        @Override
        public ExecutionResult run(AgentDefinition definition, ExecutionRequest request, AgentRunContext initialState) {
            if (definition instanceof ReflexionCriticDefinition criticDefinition) {
                criticStates.add(initialState);
                criticAllowedTools.add(request.allowedTools());
                Decision.Complete complete = (Decision.Complete) criticDefinition.decide(
                        initialState.withRequest(request).withToolSpecifications(List.of())
                );
                return new ExecutionResult(complete.result(), initialState.currentContent(), initialState.markCompleted());
            }

            actorStates.add(initialState);
            actorAllowedTools.add(request.allowedTools());
            Decision.Complete complete = (Decision.Complete) definition.decide(
                    initialState.withRequest(request).withToolSpecifications(List.of())
            );
            return new ExecutionResult(
                    complete.result(),
                    complete.result(),
                    initialState.withCurrentContent(complete.result()).markCompleted()
            );
        }

        @Override
        public ExecutionResult run(AgentDefinition definition, ExecutionRequest request) {
            return run(definition, request, new AgentRunContext(0, request.document().content()));
        }
    }

    private static final class QueueChatModel implements ChatModel {

        private final Deque<String> responses;

        private QueueChatModel(String... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(responses.removeFirst()))
                    .build();
        }
    }
}
