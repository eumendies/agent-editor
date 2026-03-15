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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class HybridSupervisorAgentDefinitionTest {

    @Test
    void shouldPreferAnalyzerOnFirstPassWhenInstructionNeedsInspection() {
        HybridSupervisorAgentDefinition definition = new HybridSupervisorAgentDefinition(
                new RecordingChatModel("""
                        {"action":"assign_worker","worker_id":"editor","instruction":"Start editing","reasoning":"guess"}
                        """)
        );

        SupervisorDecision decision = definition.decide(new SupervisorContext(
                "task-1",
                "session-1",
                "Inspect the document before making changes",
                "Draft body",
                workers(),
                List.of()
        ));

        SupervisorDecision.AssignWorker assignWorker = assertInstanceOf(SupervisorDecision.AssignWorker.class, decision);
        assertEquals("analyzer", assignWorker.workerId());
    }

    @Test
    void shouldReturnAssignedWorkerSelectedByModelWithinCandidates() {
        HybridSupervisorAgentDefinition definition = new HybridSupervisorAgentDefinition(
                new RecordingChatModel("""
                        {"action":"assign_worker","worker_id":"editor","instruction":"Apply the approved revision","reasoning":"editing is next"}
                        """)
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
    void shouldFallbackToRuleBasedWorkerWhenModelReturnsUnknownWorker() {
        HybridSupervisorAgentDefinition definition = new HybridSupervisorAgentDefinition(
                new RecordingChatModel("""
                        {"action":"assign_worker","worker_id":"translator","instruction":"Do something else","reasoning":"bad output"}
                        """)
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
        assertEquals("editor", assignWorker.workerId());
    }

    @Test
    void shouldCompleteWhenModelRequestsCompletion() {
        HybridSupervisorAgentDefinition definition = new HybridSupervisorAgentDefinition(
                new RecordingChatModel("""
                        {"action":"complete","final_content":"Final body","summary":"work complete","reasoning":"done"}
                        """)
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
                new RecordingChatModel("""
                        {"action":"assign_worker","worker_id":"analyzer","instruction":"Inspect again","reasoning":"keep trying"}
                        """)
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
                new RecordingChatModel("""
                        {"action":"assign_worker","worker_id":"editor","instruction":"Try editing again","reasoning":"continue"}
                        """)
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
                new RecordingChatModel("""
                        {"action":"assign_worker","worker_id":"analyzer","instruction":"Inspect again","reasoning":"one more pass"}
                        """)
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
