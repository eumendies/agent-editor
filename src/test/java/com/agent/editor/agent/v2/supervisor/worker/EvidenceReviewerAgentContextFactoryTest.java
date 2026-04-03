package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.support.NoOpMemoryCompressors;
import com.agent.editor.agent.v2.tool.document.DocumentToolMode;
import com.agent.editor.agent.v2.tool.document.DocumentToolNames;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceReviewerAgentContextFactoryTest {

    @Test
    void shouldBuildInvocationContextFromTranscriptOrderWithoutInjectingInstructionAheadOfHistory() {
        AtomicInteger compressionCalls = new AtomicInteger();
        EvidenceReviewerAgentContextFactory factory = new EvidenceReviewerAgentContextFactory(request -> {
            compressionCalls.incrementAndGet();
            return new com.agent.editor.agent.v2.core.memory.MemoryCompressionResult(
                    new ChatTranscriptMemory(List.of(
                            new ChatMessage.UserChatMessage("older reviewer turn"),
                            new ChatMessage.UserChatMessage("review whether the answer follows the instruction and evidence")
                    )),
                    true,
                    "compressed"
            );
        });

        var invocationContext = factory.buildModelInvocationContext(context());

        assertEquals(3, invocationContext.getMessages().size());
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains("reviewer worker"));
        assertTrue(systemMessage.text().contains("ReviewerFeedback"));
        assertTrue(systemMessage.text().contains("Do not wrap JSON in markdown fences or backticks."));
        assertTrue(systemMessage.text().contains("\"verdict\":\"PASS\" or \"REVISE\""));
        assertTrue(systemMessage.text().contains("\"instructionSatisfied\": true or false"));
        assertTrue(systemMessage.text().contains("\"evidenceGrounded\": true or false"));
        assertTrue(systemMessage.text().contains("\"unsupportedClaims\": []"));
        assertTrue(systemMessage.text().contains("\"missingRequirements\": []"));
        assertTrue(systemMessage.text().contains("Do not omit any field."));
        assertTrue(systemMessage.text().contains("Valid output example:"));
        UserMessage historyMessage = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(1));
        assertEquals("older reviewer turn", historyMessage.singleText());
        UserMessage currentTurnMessage = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(2));
        assertEquals("review whether the answer follows the instruction and evidence", currentTurnMessage.singleText());
        assertEquals(0, compressionCalls.get());
    }

    @Test
    void shouldPrepareInitialContextByAppendingCurrentInstructionToTranscript() {
        EvidenceReviewerAgentContextFactory factory = new EvidenceReviewerAgentContextFactory(NoOpMemoryCompressors.noop());

        AgentRunContext context = factory.prepareInitialContext(new com.agent.editor.agent.v2.task.TaskRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "review whether the answer follows the instruction and evidence",
                3,
                new ChatTranscriptMemory(List.of(new ChatMessage.UserChatMessage("raw reviewer memory")))
        ));

        ChatTranscriptMemory memory = assertInstanceOf(ChatTranscriptMemory.class, context.getMemory());
        assertEquals(2, memory.getMessages().size());
        assertEquals("raw reviewer memory", memory.getMessages().get(0).getText());
        assertEquals("review whether the answer follows the instruction and evidence", memory.getMessages().get(1).getText());
    }

    @Test
    void shouldDescribeSnapshotWorkflowWhenFullReviewToolsAreVisible() {
        EvidenceReviewerAgentContextFactory factory = new EvidenceReviewerAgentContextFactory(NoOpMemoryCompressors.noop());

        var invocationContext = factory.buildModelInvocationContext(context().withToolSpecifications(List.of(
                ToolSpecification.builder()
                        .name(DocumentToolNames.GET_DOCUMENT_SNAPSHOT)
                        .description("get full document")
                        .build()
        )));

        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains(DocumentToolNames.GET_DOCUMENT_SNAPSHOT));
        assertTrue(systemMessage.text().contains("## Current Document Content"));
        assertTrue(systemMessage.text().contains("body"));
        assertTrue(!systemMessage.text().contains("## Document Model"));
        assertTrue(!systemMessage.text().contains("## Document Structure JSON"));
        assertTrue(!systemMessage.text().contains("nodeId"));
        assertTrue(!systemMessage.text().contains("Use " + DocumentToolNames.READ_DOCUMENT_NODE + " for targeted reads when the document is too large for a full snapshot."));
    }

    private AgentRunContext context() {
        EvidenceReviewerAgentContextFactory factory = new EvidenceReviewerAgentContextFactory(NoOpMemoryCompressors.noop());
        return factory.prepareInitialContext(new com.agent.editor.agent.v2.task.TaskRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "review whether the answer follows the instruction and evidence",
                3,
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("older reviewer turn")
                ))
        )).withRequest(fullRequest());
    }

    private ExecutionRequest fullRequest() {
        ExecutionRequest request = new ExecutionRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "review whether the answer follows the instruction and evidence",
                3
        );
        request.setDocumentToolMode(DocumentToolMode.FULL);
        return request;
    }
}
