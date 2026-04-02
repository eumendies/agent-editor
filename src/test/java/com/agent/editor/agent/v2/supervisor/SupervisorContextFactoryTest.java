package com.agent.editor.agent.v2.supervisor;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.context.ModelInvocationContext;
import com.agent.editor.agent.v2.core.context.SupervisorContext;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.MemoryCompressionResult;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.agent.v2.supervisor.SupervisorWorkerIds;
import com.agent.editor.agent.v2.support.NoOpMemoryCompressors;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ResponseFormatType;
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
        SupervisorContextFactory factory = new SupervisorContextFactory(NoOpMemoryCompressors.noop());

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
        assertEquals(2, memory.getMessages().size());
        assertEquals("previous turn", memory.getMessages().get(0).getText());
        assertEquals("Improve this document", memory.getMessages().get(1).getText());
    }

    @Test
    void shouldBuildSupervisorContextWithSnapshotCopies() {
        SupervisorContextFactory factory = new SupervisorContextFactory(NoOpMemoryCompressors.noop());
        AgentRunContext conversationState = new AgentRunContext(
                null,
                1,
                "draft",
                new ChatTranscriptMemory(List.of(new ChatMessage.UserChatMessage("summary"))),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );
        ArrayList<SupervisorContext.WorkerDefinition> availableWorkers = new ArrayList<>(List.of(worker(SupervisorWorkerIds.WRITER)));
        ArrayList<SupervisorContext.WorkerResult> workerResults = new ArrayList<>(List.of(
                new SupervisorContext.WorkerResult(SupervisorWorkerIds.RESEARCHER, TaskStatus.COMPLETED, "evidence collected", "draft")
        ));

        SupervisorContext context = factory.buildSupervisorContext(
                taskRequest(),
                conversationState,
                workerResults,
                availableWorkers
        );
        availableWorkers.add(worker(SupervisorWorkerIds.REVIEWER));
        workerResults.add(new SupervisorContext.WorkerResult(SupervisorWorkerIds.WRITER, TaskStatus.COMPLETED, "draft updated", "draft 2"));

        assertEquals(1, context.getAvailableWorkers().size());
        assertEquals(1, context.getWorkerResults().size());
        assertEquals("Improve this document", context.getRequest().getInstruction());
        assertNotSame(availableWorkers, context.getAvailableWorkers());
        assertNotSame(workerResults, context.getWorkerResults());
    }

    @Test
    void shouldBuildWorkerExecutionContextWithoutToolTranscriptLeakage() {
        SupervisorContextFactory factory = new SupervisorContextFactory(NoOpMemoryCompressors.noop());
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

        AgentRunContext workerContext = factory.buildWorkerExecutionContext(
                conversationState,
                "draft",
                "Review whether the draft is grounded"
        );

        ChatTranscriptMemory memory = (ChatTranscriptMemory) workerContext.getMemory();
        assertEquals(2, memory.getMessages().size());
        assertTrue(memory.getMessages().get(0).getText().contains("evidence collected"));
        assertEquals("Review whether the draft is grounded", memory.getMessages().get(1).getText());
        assertTrue(memory.getMessages().stream().noneMatch(ChatMessage.ToolExecutionResultChatMessage.class::isInstance));
    }

    @Test
    void shouldSummarizeWorkerResultBackIntoConversationState() {
        SupervisorContextFactory factory = new SupervisorContextFactory(NoOpMemoryCompressors.noop());
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
                SupervisorWorkerIds.WRITER,
                new ExecutionResult<>("summary", "summary", "draft updated")
        );

        assertEquals("draft updated", nextState.getCurrentContent());
        ChatTranscriptMemory memory = (ChatTranscriptMemory) nextState.getMemory();
        assertEquals(1, memory.getMessages().size());
        assertTrue(memory.getMessages().get(0).getText().contains("workerId: " + SupervisorWorkerIds.WRITER));
        assertTrue(memory.getMessages().get(0).getText().contains("summary: summary"));
    }

    @Test
    void shouldBuildRoutingInvocationContextWithCandidateAndWorkerSummaries() {
        SupervisorContextFactory factory = new SupervisorContextFactory(NoOpMemoryCompressors.noop());
        SupervisorContext context = factory.buildSupervisorContext(
                taskRequest(),
                factory.prepareInitialContext(taskRequest()),
                List.of(
                        new SupervisorContext.WorkerResult(SupervisorWorkerIds.RESEARCHER, TaskStatus.COMPLETED, "evidence collected", "body"),
                        new SupervisorContext.WorkerResult(SupervisorWorkerIds.WRITER, TaskStatus.COMPLETED, "draft updated", "body v2")
                ),
                List.of(worker(SupervisorWorkerIds.WRITER))
        );

        ModelInvocationContext invocationContext = factory.buildRoutingInvocationContext(context, context.getAvailableWorkers());

        assertEquals(4, invocationContext.getMessages().size());
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains("You are a hybrid supervisor for a document workflow"));
        UserMessage message = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(1));
        assertTrue(message.singleText().contains("Task: Improve this document"));
        assertTrue(message.singleText().contains("Current document structure:"));
        assertTrue(message.singleText().contains("Intro"));
        assertTrue(message.singleText().contains("writer | role=Writer"));
        AiMessage firstWorkerResult = assertInstanceOf(AiMessage.class, invocationContext.getMessages().get(2));
        assertTrue(firstWorkerResult.text().contains("workerId: " + SupervisorWorkerIds.RESEARCHER));
        assertTrue(firstWorkerResult.text().contains("summary: evidence collected"));
        assertTrue(!firstWorkerResult.text().contains("updatedContent:"));
        AiMessage secondWorkerResult = assertInstanceOf(AiMessage.class, invocationContext.getMessages().get(3));
        assertTrue(secondWorkerResult.text().contains("workerId: " + SupervisorWorkerIds.WRITER));
        assertTrue(secondWorkerResult.text().contains("updatedContent: body v2"));
        assertEquals(ResponseFormatType.JSON, invocationContext.getResponseFormat().type());
        assertEquals("supervisor_routing", invocationContext.getResponseFormat().jsonSchema().name());
        assertTrue(systemMessage.text().contains("If the latest worker result is from writer, the default next step is reviewer."));
        assertTrue(systemMessage.text().contains("Only choose complete when the latest content has already been reviewed"));
    }

    @Test
    void shouldCompressWorkerExecutionContextBeforeRemovingToolDetails() {
        SupervisorContextFactory factory = new SupervisorContextFactory(
                request -> new MemoryCompressionResult(
                        new ChatTranscriptMemory(List.of(
                                new ChatMessage.AiChatMessage("compressed summary"),
                                new ChatMessage.ToolExecutionResultChatMessage("call-1", "retrieveKnowledge", "{}", "raw tool transcript")
                        )),
                        true,
                        "compressed"
                )
        );
        AgentRunContext conversationState = new AgentRunContext(
                null,
                1,
                "draft",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("Previous worker result"),
                        new ChatMessage.ToolExecutionResultChatMessage("call-0", "retrieveKnowledge", "{}", "raw")
                )),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );

        AgentRunContext workerContext = factory.buildWorkerExecutionContext(
                conversationState,
                "draft",
                "Gather more evidence"
        );

        ChatTranscriptMemory memory = (ChatTranscriptMemory) workerContext.getMemory();
        assertEquals(2, memory.getMessages().size());
        assertEquals("compressed summary", memory.getMessages().get(0).getText());
        assertEquals("Gather more evidence", memory.getMessages().get(1).getText());
    }

    private static TaskRequest taskRequest() {
        return new TaskRequest(
                "task-1",
                "session-1",
                AgentType.SUPERVISOR,
                new DocumentSnapshot("doc-1", "Title", "# Intro\n\nbody"),
                "Improve this document",
                5
        );
    }

    private static SupervisorContext.WorkerDefinition worker(String workerId) {
        return new SupervisorContext.WorkerDefinition(
                workerId,
                workerId.equals(SupervisorWorkerIds.WRITER) ? "Writer" : "Reviewer",
                workerId.equals(SupervisorWorkerIds.WRITER) ? "Update the document" : "Review the document",
                new NoOpAgent(),
                List.of("editDocument"),
                List.of(workerId.equals(SupervisorWorkerIds.WRITER) ? "write" : "review")
        );
    }

    private static final class NoOpAgent implements Agent {

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }
    }
}
