package com.agent.editor.agent.v2.supervisor;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.context.ModelInvocationContext;
import com.agent.editor.agent.v2.core.context.SupervisorContext;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.agent.v2.task.TaskRequest;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisorContextFactoryTest {

    @Test
    void shouldPrepareInitialSupervisorConversationStateFromTaskRequest() {
        SupervisorContextFactory factory = new SupervisorContextFactory();

        AgentRunContext context = factory.prepareInitialContext(new TaskRequest(
                "task-1",
                "session-1",
                AgentType.SUPERVISOR,
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve this document",
                5,
                new ChatTranscriptMemory(List.of(new ChatMessage.UserChatMessage("previous turn")))
        ));

        assertEquals("body", context.getCurrentContent());
        ChatTranscriptMemory memory = (ChatTranscriptMemory) context.getMemory();
        assertEquals(1, memory.getMessages().size());
        assertEquals("previous turn", memory.getMessages().get(0).getText());
    }

    @Test
    void shouldBuildSupervisorContextWithSnapshotCopies() {
        SupervisorContextFactory factory = new SupervisorContextFactory();
        AgentRunContext conversationState = new AgentRunContext(
                null,
                1,
                "draft",
                new ChatTranscriptMemory(List.of(new ChatMessage.UserChatMessage("summary"))),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );
        ArrayList<SupervisorContext.WorkerDefinition> availableWorkers = new ArrayList<>(List.of(worker("writer")));
        ArrayList<SupervisorContext.WorkerResult> workerResults = new ArrayList<>(List.of(
                new SupervisorContext.WorkerResult("researcher", TaskStatus.COMPLETED, "evidence collected", "draft")
        ));

        SupervisorContext context = factory.buildSupervisorContext(
                taskRequest(),
                conversationState,
                workerResults,
                availableWorkers
        );
        availableWorkers.add(worker("reviewer"));
        workerResults.add(new SupervisorContext.WorkerResult("writer", TaskStatus.COMPLETED, "draft updated", "draft 2"));

        assertEquals(1, context.getAvailableWorkers().size());
        assertEquals(1, context.getWorkerResults().size());
        assertEquals("Improve this document", context.getRequest().getInstruction());
        assertNotSame(availableWorkers, context.getAvailableWorkers());
        assertNotSame(workerResults, context.getWorkerResults());
    }

    @Test
    void shouldBuildWorkerExecutionContextWithoutToolTranscriptLeakage() {
        SupervisorContextFactory factory = new SupervisorContextFactory();
        AgentRunContext conversationState = new AgentRunContext(
                null,
                1,
                "draft",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("Previous worker result:\nworkerId: researcher\nsummary: evidence collected"),
                        new ChatMessage.ToolExecutionResultChatMessage("call-1", "retrieveKnowledge", null, "raw tool transcript")
                )),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );

        AgentRunContext workerContext = factory.buildWorkerExecutionContext(conversationState, "draft");

        ChatTranscriptMemory memory = (ChatTranscriptMemory) workerContext.getMemory();
        assertEquals(1, memory.getMessages().size());
        assertTrue(memory.getMessages().get(0).getText().contains("evidence collected"));
        assertTrue(memory.getMessages().stream().noneMatch(ChatMessage.ToolExecutionResultChatMessage.class::isInstance));
    }

    @Test
    void shouldSummarizeWorkerResultBackIntoConversationState() {
        SupervisorContextFactory factory = new SupervisorContextFactory();
        AgentRunContext conversationState = new AgentRunContext(
                null,
                1,
                "draft",
                new ChatTranscriptMemory(List.of()),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );

        AgentRunContext nextState = factory.summarizeWorkerResult(
                conversationState,
                "writer",
                new ExecutionResult<>("summary", "summary", "draft updated")
        );

        assertEquals("draft updated", nextState.getCurrentContent());
        ChatTranscriptMemory memory = (ChatTranscriptMemory) nextState.getMemory();
        ChatMessage.UserChatMessage summary = assertInstanceOf(ChatMessage.UserChatMessage.class, memory.getMessages().get(0));
        assertTrue(summary.getText().contains("workerId: writer"));
        assertTrue(summary.getText().contains("summary: summary"));
    }

    @Test
    void shouldBuildRoutingInvocationContextWithCandidateAndWorkerSummaries() {
        SupervisorContextFactory factory = new SupervisorContextFactory();
        SupervisorContext context = factory.buildSupervisorContext(
                taskRequest(),
                factory.prepareInitialContext(taskRequest()),
                List.of(new SupervisorContext.WorkerResult("researcher", TaskStatus.COMPLETED, "evidence collected", "body")),
                List.of(worker("writer"))
        );

        ModelInvocationContext invocationContext = factory.buildRoutingInvocationContext(context, context.getAvailableWorkers());

        assertEquals(1, invocationContext.getMessages().size());
        UserMessage message = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(0));
        assertTrue(message.singleText().contains("Task: Improve this document"));
        assertTrue(message.singleText().contains("writer | role=Writer"));
        assertTrue(message.singleText().contains("researcher: evidence collected"));
    }

    private static TaskRequest taskRequest() {
        return new TaskRequest(
                "task-1",
                "session-1",
                AgentType.SUPERVISOR,
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve this document",
                5
        );
    }

    private static SupervisorContext.WorkerDefinition worker(String workerId) {
        return new SupervisorContext.WorkerDefinition(
                workerId,
                workerId.equals("writer") ? "Writer" : "Reviewer",
                workerId.equals("writer") ? "Update the document" : "Review the document",
                new NoOpAgent(),
                List.of("editDocument"),
                List.of(workerId.equals("writer") ? "write" : "review")
        );
    }

    private static final class NoOpAgent implements Agent {

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }
    }
}
