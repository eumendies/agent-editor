package com.agent.editor.agent.v2.supervisor;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.runtime.ExecutionContext;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class HybridSupervisorAgentDefinitionTest {

    @Test
    void shouldMapStructuredResponseThroughAiService() {
        RecordingChatModel chatModel = new RecordingChatModel("""
                {"action":"ASSIGN_WORKER","workerId":"editor","instruction":"Start editing","reasoning":"guess"}
                """);
        SupervisorRoutingAiService routingAiService = AiServices.builder(SupervisorRoutingAiService.class)
                .chatModel(chatModel)
                .build();

        SupervisorRoutingResponse response = routingAiService.route(
                "Inspect the document before making changes",
                "Draft body",
                "editor | role=Editor | description=Apply document edits | capabilities=edit",
                "No worker steps executed"
        );

        assertEquals(SupervisorAction.ASSIGN_WORKER, response.action());
        assertEquals("editor", response.workerId());
        assertInstanceOf(dev.langchain4j.model.chat.request.ChatRequestParameters.class, chatModel.lastRequest.parameters());
    }

    @Test
    void shouldMapSnakeCaseActionThroughAiService() {
        RecordingChatModel chatModel = new RecordingChatModel("""
                {"action":"assign_worker","workerId":"editor","instruction":"Start editing","reasoning":"guess"}
                """);
        SupervisorRoutingAiService routingAiService = AiServices.builder(SupervisorRoutingAiService.class)
                .chatModel(chatModel)
                .build();

        SupervisorRoutingResponse response = routingAiService.route(
                "Inspect the document before making changes",
                "Draft body",
                "editor | role=Editor | description=Apply document edits | capabilities=edit",
                "No worker steps executed"
        );

        assertEquals(SupervisorAction.ASSIGN_WORKER, response.action());
        assertEquals("editor", response.workerId());
    }

    @Test
    void shouldReturnAssignedWorkerSelectedByServiceWithinCandidates() {
        HybridSupervisorAgentDefinition definition = new HybridSupervisorAgentDefinition(
                new StubSupervisorRoutingAiService(new SupervisorRoutingResponse(
                        SupervisorAction.ASSIGN_WORKER,
                        "editor",
                        "Apply the approved revision",
                        null,
                        null,
                        "editing is next"
                ))
        );

        SupervisorDecision decision = definition.decide(new SupervisorContext(
                "task-2",
                "session-1",
                "Revise the tone of the introduction",
                "Draft body",
                workers(),
                List.of(new WorkerResult("analyzer", TaskStatus.COMPLETED, "analysis complete", "Draft body"))
        ));

        SupervisorDecision.AssignWorker assignWorker = assertInstanceOf(SupervisorDecision.AssignWorker.class, decision);
        assertEquals("editor", assignWorker.workerId());
        assertEquals("Apply the approved revision", assignWorker.instruction());
        assertEquals("editing is next", assignWorker.reasoning());
    }

    @Test
    void shouldFallbackToFirstLegalWorkerWhenServiceReturnsUnknownWorker() {
        HybridSupervisorAgentDefinition definition = new HybridSupervisorAgentDefinition(
                new StubSupervisorRoutingAiService(new SupervisorRoutingResponse(
                        SupervisorAction.ASSIGN_WORKER,
                        "translator",
                        "Do something else",
                        null,
                        null,
                        "bad output"
                ))
        );

        SupervisorDecision decision = definition.decide(new SupervisorContext(
                "task-3",
                "session-1",
                "Revise the introduction for clarity",
                "Draft body",
                workers(),
                List.of(new WorkerResult("analyzer", TaskStatus.COMPLETED, "analysis complete", "Draft body"))
        ));

        SupervisorDecision.AssignWorker assignWorker = assertInstanceOf(SupervisorDecision.AssignWorker.class, decision);
        assertEquals("analyzer", assignWorker.workerId());
    }

    @Test
    void shouldCompleteWhenServiceRequestsCompletion() {
        HybridSupervisorAgentDefinition definition = new HybridSupervisorAgentDefinition(
                new StubSupervisorRoutingAiService(new SupervisorRoutingResponse(
                        SupervisorAction.COMPLETE,
                        null,
                        null,
                        "work complete",
                        "Final body",
                        "done"
                ))
        );

        SupervisorDecision decision = definition.decide(new SupervisorContext(
                "task-4",
                "session-1",
                "Review the revised draft",
                "Draft body",
                workers(),
                List.of(
                        new WorkerResult("analyzer", TaskStatus.COMPLETED, "analysis complete", "Draft body"),
                        new WorkerResult("editor", TaskStatus.COMPLETED, "edited", "Final body")
                )
        ));

        SupervisorDecision.Complete complete = assertInstanceOf(SupervisorDecision.Complete.class, decision);
        assertEquals("Final body", complete.finalContent());
        assertEquals("work complete", complete.summary());
        assertEquals("done", complete.reasoning());
    }

    @Test
    void shouldCompleteWhenNoCandidateWorkersRemain() {
        HybridSupervisorAgentDefinition definition = new HybridSupervisorAgentDefinition(
                new StubSupervisorRoutingAiService(new SupervisorRoutingResponse(
                        SupervisorAction.ASSIGN_WORKER,
                        "analyzer",
                        "Inspect again",
                        null,
                        null,
                        "keep trying"
                ))
        );

        SupervisorDecision decision = definition.decide(new SupervisorContext(
                "task-5",
                "session-1",
                "Inspect the document before making changes",
                "Draft body",
                List.of(workers().get(0)),
                List.of(
                        new WorkerResult("analyzer", TaskStatus.COMPLETED, "analysis complete", "Draft body"),
                        new WorkerResult("analyzer", TaskStatus.COMPLETED, "analysis complete", "Draft body")
                )
        ));

        SupervisorDecision.Complete complete = assertInstanceOf(SupervisorDecision.Complete.class, decision);
        assertEquals("Draft body", complete.finalContent());
    }

    @Test
    void shouldStopAfterRepeatedNoProgress() {
        HybridSupervisorAgentDefinition definition = new HybridSupervisorAgentDefinition(
                new StubSupervisorRoutingAiService(new SupervisorRoutingResponse(
                        SupervisorAction.ASSIGN_WORKER,
                        "editor",
                        "Try editing again",
                        null,
                        null,
                        "continue"
                ))
        );

        SupervisorDecision decision = definition.decide(new SupervisorContext(
                "task-6",
                "session-1",
                "Revise the introduction for clarity",
                "Draft body",
                workers(),
                List.of(
                        new WorkerResult("editor", TaskStatus.COMPLETED, "no changes needed", "Draft body"),
                        new WorkerResult("editor", TaskStatus.COMPLETED, "no changes needed", "Draft body")
                )
        ));

        SupervisorDecision.Complete complete = assertInstanceOf(SupervisorDecision.Complete.class, decision);
        assertEquals("Draft body", complete.finalContent());
    }

    @Test
    void shouldDemoteSameWorkerAfterConsecutiveSelections() {
        HybridSupervisorAgentDefinition definition = new HybridSupervisorAgentDefinition(
                new StubSupervisorRoutingAiService(new SupervisorRoutingResponse(
                        SupervisorAction.ASSIGN_WORKER,
                        "analyzer",
                        "Inspect again",
                        null,
                        null,
                        "one more pass"
                ))
        );

        SupervisorDecision decision = definition.decide(new SupervisorContext(
                "task-7",
                "session-1",
                "Coordinate the next step for this document",
                "Draft body",
                workers(),
                List.of(
                        new WorkerResult("analyzer", TaskStatus.COMPLETED, "first pass", "Draft body"),
                        new WorkerResult("analyzer", TaskStatus.COMPLETED, "second pass", "Draft body with notes")
                )
        ));

        SupervisorDecision.AssignWorker assignWorker = assertInstanceOf(SupervisorDecision.AssignWorker.class, decision);
        assertEquals("editor", assignWorker.workerId());
    }

    private static List<WorkerDefinition> workers() {
        AgentDefinition workerAgent = new NoOpWorkerAgent();
        return List.of(
                new WorkerDefinition(
                        "analyzer",
                        "Analyzer",
                        "Inspect the document",
                        workerAgent,
                        List.of("searchContent", "analyzeDocument"),
                        List.of("analyze")
                ),
                new WorkerDefinition(
                        "editor",
                        "Editor",
                        "Apply document edits",
                        workerAgent,
                        List.of("editDocument"),
                        List.of("edit")
                ),
                new WorkerDefinition(
                        "reviewer",
                        "Reviewer",
                        "Review the updated draft",
                        workerAgent,
                        List.of("searchContent", "analyzeDocument"),
                        List.of("review")
                )
        );
    }

    private static final class RecordingChatModel implements ChatModel {

        private final String response;
        private ChatRequest lastRequest;

        private RecordingChatModel(String response) {
            this.response = response;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            this.lastRequest = request;
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(response))
                    .build();
        }
    }

    private static final class StubSupervisorRoutingAiService implements SupervisorRoutingAiService {

        private final SupervisorRoutingResponse response;

        private StubSupervisorRoutingAiService(SupervisorRoutingResponse response) {
            this.response = response;
        }

        @Override
        public SupervisorRoutingResponse route(String instruction, String currentContent, String candidates, String previousWorkerResults) {
            return response;
        }
    }

    private static final class NoOpWorkerAgent implements AgentDefinition {

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public Decision decide(ExecutionContext context) {
            return new Decision.Complete("unused", "unused");
        }
    }
}
