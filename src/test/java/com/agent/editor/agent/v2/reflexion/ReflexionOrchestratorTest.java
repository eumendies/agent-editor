package com.agent.editor.agent.v2.reflexion;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.ToolLoopAgent;
import com.agent.editor.agent.v2.core.agent.ToolLoopDecision;
import com.agent.editor.agent.v2.core.runtime.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.state.TaskStatus;
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
                new ActorAgent(),
                criticWithResponses("""
                        {"verdict":"PASS","feedback":"Looks good","reasoning":"done"}
                        """),
                event -> {}
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-reflex-1",
                "session-reflex-1",
                AgentType.REFLEXION,
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve the draft",
                3
        ));

        assertEquals(TaskStatus.COMPLETED, result.getStatus());
        assertEquals("body -> actor-pass-1", result.getFinalContent());
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
                new ActorAgent(),
                criticWithResponses(
                        """
                        {"verdict":"REVISE","feedback":"Tighten the introduction","reasoning":"too long"}
                        """,
                        """
                        {"verdict":"PASS","feedback":"Looks good","reasoning":"done"}
                        """
                ),
                event -> {}
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

        assertEquals(TaskStatus.COMPLETED, result.getStatus());
        assertEquals("body -> actor-pass-1 -> actor-pass-2", result.getFinalContent());
        assertEquals(2, runtime.actorStates.size());
        assertEquals(2, runtime.criticStates.size());
        ChatTranscriptMemory firstActorMemory = (ChatTranscriptMemory) runtime.actorStates.get(0).getMemory();
        assertTrue(firstActorMemory.getMessages().stream().anyMatch(message -> "previous turn".equals(message.getText())));
        ChatTranscriptMemory secondActorMemory = (ChatTranscriptMemory) runtime.actorStates.get(1).getMemory();
        assertTrue(secondActorMemory.getMessages().stream().anyMatch(message ->
                message instanceof ChatMessage.UserChatMessage userMessage
                        && userMessage.getText().contains("\"verdict\":\"REVISE\"")
                        && userMessage.getText().contains("\"feedback\":\"Tighten the introduction\"")
                        && userMessage.getText().contains("\"reasoning\":\"too long\"")
        ));
        assertEquals(0, runtime.criticStates.get(0).getIteration());
        assertEquals(0, runtime.criticStates.get(1).getIteration());
    }

    @Test
    void shouldStopWhenMaxReflectionRoundsReached() {
        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        ReflexionOrchestrator orchestrator = new ReflexionOrchestrator(
                runtime,
                new ActorAgent(),
                criticWithResponses(
                        """
                        {"verdict":"REVISE","feedback":"Round 1","reasoning":"continue"}
                        """,
                        """
                        {"verdict":"REVISE","feedback":"Round 2","reasoning":"continue"}
                        """
                ),
                event -> {}
        );

        TaskResult result = orchestrator.execute(new TaskRequest(
                "task-reflex-3",
                "session-reflex-3",
                AgentType.REFLEXION,
                new DocumentSnapshot("doc-3", "Title", "body"),
                "Improve the draft",
                2
        ));

        assertEquals(TaskStatus.COMPLETED, result.getStatus());
        assertEquals("body -> actor-pass-1 -> actor-pass-2", result.getFinalContent());
        assertEquals(2, runtime.actorStates.size());
        assertEquals(2, runtime.criticStates.size());
    }

    @Test
    void shouldNotWriteTraceStagesForReviseAndPassFlow() {
        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        ReflexionOrchestrator orchestrator = new ReflexionOrchestrator(
                runtime,
                new ActorAgent(),
                criticWithResponses(
                        """
                        {"verdict":"REVISE","feedback":"Tighten the introduction","reasoning":"too long"}
                        """,
                        """
                        {"verdict":"PASS","feedback":"Looks good","reasoning":"done"}
                        """
                ),
                event -> {}
        );

        orchestrator.execute(new TaskRequest(
                "task-reflex-4",
                "session-reflex-4",
                AgentType.REFLEXION,
                new DocumentSnapshot("doc-4", "Title", "body"),
                "Improve the draft",
                3
        ));

        assertEquals(2, runtime.actorStates.size());
        assertEquals(2, runtime.criticStates.size());
    }

    private ReflexionCritic criticWithResponses(String... responses) {
        return new ReflexionCritic(
                new QueueChatModel(responses)
        );
    }

    private static final class ActorAgent implements ToolLoopAgent {

        @Override
        public AgentType type() {
            return AgentType.REFLEXION;
        }

        @Override
        public ToolLoopDecision decide(AgentRunContext context) {
            ChatTranscriptMemory transcriptMemory = (ChatTranscriptMemory) context.state().getMemory();
            long critiqueCount = transcriptMemory.getMessages().stream()
                    .filter(ChatMessage.UserChatMessage.class::isInstance)
                    .map(ChatMessage.UserChatMessage.class::cast)
                    .filter(message -> message.getText().startsWith("Reflection critique"))
                    .count();
            long round = critiqueCount + 1;
            return new ToolLoopDecision.Complete(context.state().getCurrentContent() + " -> actor-pass-" + round, "actor done");
        }
    }

    private static final class RecordingExecutionRuntime implements ExecutionRuntime {

        private final List<AgentRunContext> actorStates = new ArrayList<>();
        private final List<AgentRunContext> criticStates = new ArrayList<>();
        private final List<List<String>> actorAllowedTools = new ArrayList<>();
        private final List<List<String>> criticAllowedTools = new ArrayList<>();

        @Override
        public ExecutionResult run(Agent definition, ExecutionRequest request, AgentRunContext initialState) {
            if (definition instanceof ReflexionCritic criticDefinition) {
                criticStates.add(initialState);
                criticAllowedTools.add(request.getAllowedTools());
                ToolLoopDecision.Complete complete = (ToolLoopDecision.Complete) criticDefinition.decide(
                        initialState.withRequest(request).withToolSpecifications(List.of())
                );
                String critique = String.valueOf(complete.getResult());
                return new ExecutionResult(complete.getResult(), critique, initialState.getCurrentContent(), initialState.markCompleted());
            }

            actorStates.add(initialState);
            actorAllowedTools.add(request.getAllowedTools());
            ToolLoopDecision.Complete complete = (ToolLoopDecision.Complete) ((ToolLoopAgent) definition).decide(
                    initialState.withRequest(request).withToolSpecifications(List.of())
            );
            String actorResult = String.valueOf(complete.getResult());
            return new ExecutionResult(
                    complete.getResult(),
                    actorResult,
                    actorResult,
                    initialState.withCurrentContent(actorResult).markCompleted()
            );
        }

        @Override
        public ExecutionResult run(Agent agent, ExecutionRequest request) {
            return run(agent, request, new AgentRunContext(0, request.getDocument().getContent()));
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
