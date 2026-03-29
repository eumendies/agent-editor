package com.agent.editor.agent.v2.supervisor.routing;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.SupervisorDecision;
import com.agent.editor.agent.v2.core.agent.ToolLoopAgent;
import com.agent.editor.agent.v2.core.agent.ToolLoopDecision;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.context.SupervisorContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.supervisor.SupervisorContextFactory;
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
        HybridSupervisorAgent definition = new HybridSupervisorAgent(
                new StubSupervisorRoutingAiService(new SupervisorRoutingResponse(
                        SupervisorAction.ASSIGN_WORKER,
                        "editor",
                        "Apply the approved revision",
                        null,
                        null,
                        "editing is next"
                ))
        );

        SupervisorDecision decision = definition.decide(supervisorContext(
                "task-2",
                "session-1",
                "Revise the tone of the introduction",
                "Draft body",
                workers(),
                List.of(new SupervisorContext.WorkerResult("analyzer", TaskStatus.COMPLETED, "analysis complete", "Draft body"))
        ));

        SupervisorDecision.AssignWorker assignWorker = assertInstanceOf(SupervisorDecision.AssignWorker.class, decision);
        assertEquals("editor", assignWorker.getWorkerId());
        assertEquals("Apply the approved revision", assignWorker.getInstruction());
        assertEquals("editing is next", assignWorker.getReasoning());
    }

    @Test
    void shouldFallbackToFirstLegalWorkerWhenServiceReturnsUnknownWorker() {
        RecordingSupervisorContextFactory contextFactory = new RecordingSupervisorContextFactory();
        HybridSupervisorAgent definition = new HybridSupervisorAgent(
                new StubSupervisorRoutingAiService(new SupervisorRoutingResponse(
                        SupervisorAction.ASSIGN_WORKER,
                        "translator",
                        "Do something else",
                        null,
                        null,
                        "bad output"
                )),
                contextFactory
        );

        SupervisorDecision decision = definition.decide(supervisorContext(
                "task-3",
                "session-1",
                "Revise the introduction for clarity",
                "Draft body",
                workers(),
                List.of(new SupervisorContext.WorkerResult("analyzer", TaskStatus.COMPLETED, "analysis complete", "Draft body"))
        ));

        SupervisorDecision.AssignWorker assignWorker = assertInstanceOf(SupervisorDecision.AssignWorker.class, decision);
        assertEquals("analyzer", assignWorker.getWorkerId());
        assertEquals("factory fallback: analyzer", assignWorker.getInstruction());
        assertEquals(1, contextFactory.routingCandidateRenderCount);
        assertEquals(1, contextFactory.routingResultRenderCount);
        assertEquals(1, contextFactory.fallbackInstructionBuildCount);
    }

    @Test
    void shouldCompleteWhenServiceRequestsCompletion() {
        HybridSupervisorAgent definition = new HybridSupervisorAgent(
                new StubSupervisorRoutingAiService(new SupervisorRoutingResponse(
                        SupervisorAction.COMPLETE,
                        null,
                        null,
                        "work complete",
                        "Final body",
                        "done"
                ))
        );

        SupervisorDecision decision = definition.decide(supervisorContext(
                "task-4",
                "session-1",
                "Review the revised draft",
                "Draft body",
                workers(),
                List.of(
                        new SupervisorContext.WorkerResult("analyzer", TaskStatus.COMPLETED, "analysis complete", "Draft body"),
                        new SupervisorContext.WorkerResult("editor", TaskStatus.COMPLETED, "edited", "Final body")
                )
        ));

        SupervisorDecision.Complete complete = assertInstanceOf(SupervisorDecision.Complete.class, decision);
        assertEquals("Final body", complete.getFinalContent());
        assertEquals("work complete", complete.getSummary());
        assertEquals("done", complete.getReasoning());
    }

    @Test
    void shouldCompleteWhenNoCandidateWorkersRemain() {
        HybridSupervisorAgent definition = new HybridSupervisorAgent(
                new StubSupervisorRoutingAiService(new SupervisorRoutingResponse(
                        SupervisorAction.ASSIGN_WORKER,
                        "analyzer",
                        "Inspect again",
                        null,
                        null,
                        "keep trying"
                ))
        );

        SupervisorDecision decision = definition.decide(supervisorContext(
                "task-5",
                "session-1",
                "Inspect the document before making changes",
                "Draft body",
                List.of(workers().get(0)),
                List.of(
                        new SupervisorContext.WorkerResult("analyzer", TaskStatus.COMPLETED, "analysis complete", "Draft body"),
                        new SupervisorContext.WorkerResult("analyzer", TaskStatus.COMPLETED, "analysis complete", "Draft body")
                )
        ));

        SupervisorDecision.Complete complete = assertInstanceOf(SupervisorDecision.Complete.class, decision);
        assertEquals("Draft body", complete.getFinalContent());
    }

    @Test
    void shouldStopAfterRepeatedNoProgress() {
        HybridSupervisorAgent definition = new HybridSupervisorAgent(
                new StubSupervisorRoutingAiService(new SupervisorRoutingResponse(
                        SupervisorAction.ASSIGN_WORKER,
                        "editor",
                        "Try editing again",
                        null,
                        null,
                        "continue"
                ))
        );

        SupervisorDecision decision = definition.decide(supervisorContext(
                "task-6",
                "session-1",
                "Revise the introduction for clarity",
                "Draft body",
                workers(),
                List.of(
                        new SupervisorContext.WorkerResult("editor", TaskStatus.COMPLETED, "no changes needed", "Draft body"),
                        new SupervisorContext.WorkerResult("editor", TaskStatus.COMPLETED, "no changes needed", "Draft body")
                )
        ));

        SupervisorDecision.Complete complete = assertInstanceOf(SupervisorDecision.Complete.class, decision);
        assertEquals("Draft body", complete.getFinalContent());
    }

    @Test
    void shouldDemoteSameWorkerAfterConsecutiveSelections() {
        HybridSupervisorAgent definition = new HybridSupervisorAgent(
                new StubSupervisorRoutingAiService(new SupervisorRoutingResponse(
                        SupervisorAction.ASSIGN_WORKER,
                        "analyzer",
                        "Inspect again",
                        null,
                        null,
                        "one more pass"
                ))
        );

        SupervisorDecision decision = definition.decide(supervisorContext(
                "task-7",
                "session-1",
                "Coordinate the next step for this document",
                "Draft body",
                workers(),
                List.of(
                        new SupervisorContext.WorkerResult("analyzer", TaskStatus.COMPLETED, "first pass", "Draft body"),
                        new SupervisorContext.WorkerResult("analyzer", TaskStatus.COMPLETED, "second pass", "Draft body with notes")
                )
        ));

        SupervisorDecision.AssignWorker assignWorker = assertInstanceOf(SupervisorDecision.AssignWorker.class, decision);
        assertEquals("editor", assignWorker.getWorkerId());
    }

    @Test
    void shouldUseRoutingServiceToSelectResearcherForInitialDispatch() {
        HybridSupervisorAgent definition = new HybridSupervisorAgent(
                new StubSupervisorRoutingAiService(new SupervisorRoutingResponse(
                        SupervisorAction.ASSIGN_WORKER,
                        "researcher",
                        "Collect supporting evidence from the knowledge base",
                        null,
                        null,
                        "fact-heavy task"
                ))
        );

        SupervisorDecision decision = definition.decide(supervisorContext(
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
        HybridSupervisorAgent definition = new HybridSupervisorAgent(
                new StubSupervisorRoutingAiService(new SupervisorRoutingResponse(
                        SupervisorAction.ASSIGN_WORKER,
                        "writer",
                        "Rewrite the draft while preserving intent",
                        null,
                        null,
                        "rewrite task"
                ))
        );

        SupervisorDecision decision = definition.decide(supervisorContext(
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
        HybridSupervisorAgent definition = new HybridSupervisorAgent((SupervisorRoutingAiService) null);

        SupervisorDecision decision = definition.decide(supervisorContext(
                "task-10",
                "session-1",
                "Write an answer grounded in my project materials",
                "Final answer",
                ragWorkers(),
                List.of(
                        new SupervisorContext.WorkerResult("researcher", TaskStatus.COMPLETED, evidenceSummary(), "Draft body"),
                        new SupervisorContext.WorkerResult("writer", TaskStatus.COMPLETED, "updated answer", "Final answer"),
                        new SupervisorContext.WorkerResult("reviewer", TaskStatus.COMPLETED, reviewerPass(), "Final answer")
                )
        ));

        SupervisorDecision.Complete complete = assertInstanceOf(SupervisorDecision.Complete.class, decision);
        assertEquals("Final answer", complete.getFinalContent());
    }

    @Test
    void shouldReturnToWriterWhenReviewerOnlyFindsInstructionGap() {
        HybridSupervisorAgent definition = new HybridSupervisorAgent((SupervisorRoutingAiService) null);

        SupervisorDecision decision = definition.decide(supervisorContext(
                "task-11",
                "session-1",
                "Write an answer grounded in my project materials",
                "Draft answer",
                ragWorkers(),
                List.of(
                        new SupervisorContext.WorkerResult("researcher", TaskStatus.COMPLETED, evidenceSummary(), "Draft body"),
                        new SupervisorContext.WorkerResult("writer", TaskStatus.COMPLETED, "updated answer", "Draft answer"),
                        new SupervisorContext.WorkerResult("reviewer", TaskStatus.COMPLETED, reviewerInstructionGap(), "Draft answer")
                )
        ));

        SupervisorDecision.AssignWorker assignWorker = assertInstanceOf(SupervisorDecision.AssignWorker.class, decision);
        assertEquals("writer", assignWorker.getWorkerId());
    }

    @Test
    void shouldMakeResearcherEligibleAgainWhenReviewerFlagsUnsupportedClaims() {
        HybridSupervisorAgent definition = new HybridSupervisorAgent((SupervisorRoutingAiService) null);

        SupervisorDecision decision = definition.decide(supervisorContext(
                "task-12",
                "session-1",
                "Write an answer grounded in my project materials",
                "Draft answer",
                ragWorkers(),
                List.of(
                        new SupervisorContext.WorkerResult("researcher", TaskStatus.COMPLETED, evidenceSummary(), "Draft body"),
                        new SupervisorContext.WorkerResult("writer", TaskStatus.COMPLETED, "updated answer", "Draft answer"),
                        new SupervisorContext.WorkerResult("reviewer", TaskStatus.COMPLETED, reviewerUnsupportedClaim(), "Draft answer")
                )
        ));

        SupervisorDecision.AssignWorker assignWorker = assertInstanceOf(SupervisorDecision.AssignWorker.class, decision);
        assertEquals("researcher", assignWorker.getWorkerId());
    }

    private static SupervisorContext supervisorContext(String taskId,
                                                       String sessionId,
                                                       String instruction,
                                                       String currentContent,
                                                       List<SupervisorContext.WorkerDefinition> availableWorkers,
                                                       List<SupervisorContext.WorkerResult> workerResults) {
        SupervisorContext context = SupervisorContext.builder()
                .request(new ExecutionRequest(
                        taskId,
                        sessionId,
                        AgentType.SUPERVISOR,
                        new DocumentSnapshot("doc-" + taskId, "Title", currentContent),
                        instruction,
                        5
                ))
                .iteration(0)
                .currentContent(currentContent)
                .memory(new ChatTranscriptMemory(List.of()))
                .stage(ExecutionStage.RUNNING)
                .pendingReason(null)
                .toolSpecifications(List.of())
                .availableWorkers(availableWorkers)
                .workerResults(workerResults)
                .build();
        return context;
    }

    private static List<SupervisorContext.WorkerDefinition> workers() {
        Agent workerAgent = new NoOpWorkerAgent();
        return List.of(
                new SupervisorContext.WorkerDefinition(
                        "analyzer",
                        "Analyzer",
                        "Inspect the document",
                        workerAgent,
                        List.of("searchContent", "analyzeDocument"),
                        List.of("analyze")
                ),
                new SupervisorContext.WorkerDefinition(
                        "editor",
                        "Editor",
                        "Apply document edits",
                        workerAgent,
                        List.of("editDocument", "appendToDocument", "getDocumentSnapshot"),
                        List.of("edit")
                ),
                new SupervisorContext.WorkerDefinition(
                        "reviewer",
                        "Reviewer",
                        "Review the updated draft",
                        workerAgent,
                        List.of("searchContent", "analyzeDocument"),
                        List.of("review")
                )
        );
    }

    private static List<SupervisorContext.WorkerDefinition> ragWorkers() {
        Agent workerAgent = new NoOpWorkerAgent();
        return List.of(
                new SupervisorContext.WorkerDefinition(
                        "researcher",
                        "Researcher",
                        "Collect evidence from the knowledge base.",
                        workerAgent,
                        List.of("retrieveKnowledge"),
                        List.of("research")
                ),
                new SupervisorContext.WorkerDefinition(
                        "writer",
                        "Writer",
                        "Write or revise the document.",
                        workerAgent,
                        List.of("editDocument", "appendToDocument", "getDocumentSnapshot"),
                        List.of("write")
                ),
                new SupervisorContext.WorkerDefinition(
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

    private static final class RecordingSupervisorContextFactory extends SupervisorContextFactory {

        private int routingCandidateRenderCount;
        private int routingResultRenderCount;
        private int fallbackInstructionBuildCount;

        @Override
        public String renderCandidates(List<SupervisorContext.WorkerDefinition> candidates) {
            routingCandidateRenderCount++;
            return super.renderCandidates(candidates);
        }

        @Override
        public String renderWorkerResults(List<SupervisorContext.WorkerResult> workerResults) {
            routingResultRenderCount++;
            return super.renderWorkerResults(workerResults);
        }

        @Override
        public String buildFallbackInstruction(SupervisorContext.WorkerDefinition worker, SupervisorContext context) {
            fallbackInstructionBuildCount++;
            return "factory fallback: " + worker.getWorkerId();
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
