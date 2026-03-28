package com.agent.editor.agent.v2.supervisor.routing;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.ToolLoopAgent;
import com.agent.editor.agent.v2.core.agent.ToolLoopDecision;
import com.agent.editor.agent.v2.core.runtime.AgentRunContext;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.agent.v2.supervisor.SupervisorContext;
import com.agent.editor.agent.v2.supervisor.SupervisorDecision;
import com.agent.editor.agent.v2.supervisor.worker.WorkerDefinition;
import com.agent.editor.agent.v2.supervisor.worker.WorkerResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class HybridSupervisorAgentTest {

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

        assertEquals(SupervisorAction.ASSIGN_WORKER, response.getAction());
        assertEquals("editor", response.getWorkerId());
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

        assertEquals(SupervisorAction.ASSIGN_WORKER, response.getAction());
        assertEquals("editor", response.getWorkerId());
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
        assertEquals("editor", assignWorker.getWorkerId());
        assertEquals("Apply the approved revision", assignWorker.getInstruction());
        assertEquals("editing is next", assignWorker.getReasoning());
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
        assertEquals("analyzer", assignWorker.getWorkerId());
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
        assertEquals("Final body", complete.getFinalContent());
        assertEquals("work complete", complete.getSummary());
        assertEquals("done", complete.getReasoning());
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
        assertEquals("Draft body", complete.getFinalContent());
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
        assertEquals("Draft body", complete.getFinalContent());
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
        assertEquals("editor", assignWorker.getWorkerId());
    }

    @Test
    void shouldUseRoutingServiceToSelectResearcherForInitialDispatch() {
        HybridSupervisorAgentDefinition definition = new HybridSupervisorAgentDefinition(
                new StubSupervisorRoutingAiService(new SupervisorRoutingResponse(
                        SupervisorAction.ASSIGN_WORKER,
                        "researcher",
                        "Collect supporting evidence from the knowledge base",
                        null,
                        null,
                        "fact-heavy task"
                ))
        );

        SupervisorDecision decision = definition.decide(new SupervisorContext(
                "task-8",
                "session-1",
                "Write an answer about this project using my knowledge base details and technical facts",
                "Draft body",
                ragWorkers(),
                List.of()
        ));

        SupervisorDecision.AssignWorker assignWorker = assertInstanceOf(SupervisorDecision.AssignWorker.class, decision);
        assertEquals("researcher", assignWorker.getWorkerId());
    }

    @Test
    void shouldUseRoutingServiceToSelectWriterForInitialDispatch() {
        HybridSupervisorAgentDefinition definition = new HybridSupervisorAgentDefinition(
                new StubSupervisorRoutingAiService(new SupervisorRoutingResponse(
                        SupervisorAction.ASSIGN_WORKER,
                        "writer",
                        "Rewrite the draft while preserving intent",
                        null,
                        null,
                        "rewrite task"
                ))
        );

        SupervisorDecision decision = definition.decide(new SupervisorContext(
                "task-9",
                "session-1",
                "Polish the introduction and make it more concise without adding new facts",
                "Draft body",
                ragWorkers(),
                List.of()
        ));

        SupervisorDecision.AssignWorker assignWorker = assertInstanceOf(SupervisorDecision.AssignWorker.class, decision);
        assertEquals("writer", assignWorker.getWorkerId());
    }

    @Test
    void shouldCompleteWhenReviewerPassesInstructionAndGroundingChecks() {
        HybridSupervisorAgentDefinition definition = new HybridSupervisorAgentDefinition((SupervisorRoutingAiService) null);

        SupervisorDecision decision = definition.decide(new SupervisorContext(
                "task-10",
                "session-1",
                "Write an answer grounded in my project materials",
                "Final answer",
                ragWorkers(),
                List.of(
                        new WorkerResult("researcher", TaskStatus.COMPLETED, evidenceSummary(), "Draft body"),
                        new WorkerResult("writer", TaskStatus.COMPLETED, "updated answer", "Final answer"),
                        new WorkerResult("reviewer", TaskStatus.COMPLETED, reviewerPass(), "Final answer")
                )
        ));

        SupervisorDecision.Complete complete = assertInstanceOf(SupervisorDecision.Complete.class, decision);
        assertEquals("Final answer", complete.getFinalContent());
    }

    @Test
    void shouldReturnToWriterWhenReviewerOnlyFindsInstructionGap() {
        HybridSupervisorAgentDefinition definition = new HybridSupervisorAgentDefinition((SupervisorRoutingAiService) null);

        SupervisorDecision decision = definition.decide(new SupervisorContext(
                "task-11",
                "session-1",
                "Write an answer grounded in my project materials",
                "Draft answer",
                ragWorkers(),
                List.of(
                        new WorkerResult("researcher", TaskStatus.COMPLETED, evidenceSummary(), "Draft body"),
                        new WorkerResult("writer", TaskStatus.COMPLETED, "updated answer", "Draft answer"),
                        new WorkerResult("reviewer", TaskStatus.COMPLETED, reviewerInstructionGap(), "Draft answer")
                )
        ));

        SupervisorDecision.AssignWorker assignWorker = assertInstanceOf(SupervisorDecision.AssignWorker.class, decision);
        assertEquals("writer", assignWorker.getWorkerId());
    }

    @Test
    void shouldMakeResearcherEligibleAgainWhenReviewerFlagsUnsupportedClaims() {
        HybridSupervisorAgentDefinition definition = new HybridSupervisorAgentDefinition((SupervisorRoutingAiService) null);

        SupervisorDecision decision = definition.decide(new SupervisorContext(
                "task-12",
                "session-1",
                "Write an answer grounded in my project materials",
                "Draft answer",
                ragWorkers(),
                List.of(
                        new WorkerResult("researcher", TaskStatus.COMPLETED, evidenceSummary(), "Draft body"),
                        new WorkerResult("writer", TaskStatus.COMPLETED, "updated answer", "Draft answer"),
                        new WorkerResult("reviewer", TaskStatus.COMPLETED, reviewerUnsupportedClaim(), "Draft answer")
                )
        ));

        SupervisorDecision.AssignWorker assignWorker = assertInstanceOf(SupervisorDecision.AssignWorker.class, decision);
        assertEquals("researcher", assignWorker.getWorkerId());
    }

    private static List<WorkerDefinition> workers() {
        Agent workerAgent = new NoOpWorkerAgent();
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
                        List.of("editDocument", "appendToDocument", "getDocumentSnapshot"),
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

    private static List<WorkerDefinition> ragWorkers() {
        Agent workerAgent = new NoOpWorkerAgent();
        return List.of(
                new WorkerDefinition(
                        "researcher",
                        "Researcher",
                        "Collect evidence from the knowledge base.",
                        workerAgent,
                        List.of("retrieveKnowledge"),
                        List.of("research")
                ),
                new WorkerDefinition(
                        "writer",
                        "Writer",
                        "Write or revise the document.",
                        workerAgent,
                        List.of("editDocument", "appendToDocument", "getDocumentSnapshot"),
                        List.of("write")
                ),
                new WorkerDefinition(
                        "reviewer",
                        "Reviewer",
                        "Check instruction completion and evidence grounding.",
                        workerAgent,
                        List.of("searchContent"),
                        List.of("review")
                )
        );
    }

    private static String evidenceSummary() {
        return """
                {"queries":["agentic rag"],"evidenceSummary":"grounded evidence","limitations":"no metrics","uncoveredPoints":[],"chunks":[]}
                """;
    }

    private static String reviewerPass() {
        return """
                {"verdict":"PASS","instructionSatisfied":true,"evidenceGrounded":true,"unsupportedClaims":[],"missingRequirements":[],"feedback":"looks good","reasoning":"complete"}
                """;
    }

    private static String reviewerInstructionGap() {
        return """
                {"verdict":"REVISE","instructionSatisfied":false,"evidenceGrounded":true,"unsupportedClaims":[],"missingRequirements":["Explain project value"],"feedback":"finish the answer","reasoning":"missing requirement"}
                """;
    }

    private static String reviewerUnsupportedClaim() {
        return """
                {"verdict":"REVISE","instructionSatisfied":true,"evidenceGrounded":false,"unsupportedClaims":["Latency improved by 40%"],"missingRequirements":[],"feedback":"remove unsupported claim","reasoning":"ungrounded"}
                """;
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

    private static final class NoOpWorkerAgent implements ToolLoopAgent {

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public ToolLoopDecision decide(AgentRunContext context) {
            return new ToolLoopDecision.Complete("unused", "unused");
        }
    }
}
