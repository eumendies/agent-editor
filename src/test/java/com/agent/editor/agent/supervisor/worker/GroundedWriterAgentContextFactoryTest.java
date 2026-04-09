package com.agent.editor.agent.supervisor.worker;

import com.agent.editor.agent.core.agent.AgentType;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.core.runtime.ExecutionRequest;
import com.agent.editor.agent.core.state.DocumentSnapshot;
import com.agent.editor.agent.support.NoOpMemoryCompressors;
import com.agent.editor.agent.tool.document.DocumentToolMode;
import com.agent.editor.agent.tool.document.DocumentToolNames;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundedWriterAgentContextFactoryTest {

    @Test
    void shouldBuildInvocationContextFromTranscriptOrderWithoutInjectingInstructionAheadOfHistory() {
        AtomicInteger compressionCalls = new AtomicInteger();
        GroundedWriterAgentContextFactory factory = new GroundedWriterAgentContextFactory(request -> {
            compressionCalls.incrementAndGet();
            return new com.agent.editor.agent.core.memory.MemoryCompressionResult(
                    new ChatTranscriptMemory(List.of(
                            new ChatMessage.UserChatMessage("older writer turn"),
                            new ChatMessage.UserChatMessage("rewrite the answer using available evidence")
                    )),
                    true,
                    "compressed"
            );
        });

        var invocationContext = factory.buildModelInvocationContext(context().withToolSpecifications(List.of(
                ToolSpecification.builder()
                        .name(DocumentToolNames.READ_DOCUMENT_NODE)
                        .description("read one node")
                        .build(),
                ToolSpecification.builder()
                        .name(DocumentToolNames.PATCH_DOCUMENT_NODE)
                        .description("patch one node")
                        .build()
        )));

        assertEquals(3, invocationContext.getMessages().size());
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains("grounded writer worker"));
        assertTrue(systemMessage.text().contains(DocumentToolNames.READ_DOCUMENT_NODE));
        assertTrue(systemMessage.text().contains(DocumentToolNames.PATCH_DOCUMENT_NODE));
        assertTrue(systemMessage.text().contains("## Workflow"));
        assertTrue(systemMessage.text().contains("## Tool Rules"));
        assertTrue(systemMessage.text().contains("## Evidence Constraints"));
        assertTrue(!systemMessage.text().contains("Use editDocument when you need to replace the document content."));
        assertTrue(systemMessage.text().contains("## Document Structure JSON"));
        assertTrue(systemMessage.text().contains("must use the nodeId values from the JSON structure"));
        assertTrue(systemMessage.text().contains("\"headingText\":\"Intro\""));
        UserMessage historyMessage = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(1));
        assertEquals("older writer turn", historyMessage.singleText());
        UserMessage currentTurnMessage = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(2));
        assertEquals("rewrite the answer using available evidence", currentTurnMessage.singleText());
        assertEquals(0, compressionCalls.get());
    }

    @Test
    void shouldPrepareInitialContextByAppendingCurrentInstructionToTranscript() {
        GroundedWriterAgentContextFactory factory = new GroundedWriterAgentContextFactory(NoOpMemoryCompressors.noop());

        AgentRunContext context = factory.prepareInitialContext(new com.agent.editor.agent.task.TaskRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "# Intro\n\nbody"),
                "rewrite the answer using available evidence",
                3,
                new ChatTranscriptMemory(List.of(new ChatMessage.UserChatMessage("raw writer memory")))
        ));

        ChatTranscriptMemory memory = assertInstanceOf(ChatTranscriptMemory.class, context.getMemory());
        assertEquals(2, memory.getMessages().size());
        assertEquals("raw writer memory", memory.getMessages().get(0).getText());
        assertEquals("rewrite the answer using available evidence", memory.getMessages().get(1).getText());
    }

    @Test
    void shouldDescribeWholeDocumentWorkflowWhenSnapshotToolIsVisible() {
        GroundedWriterAgentContextFactory factory = new GroundedWriterAgentContextFactory(NoOpMemoryCompressors.noop());

        var invocationContext = factory.buildModelInvocationContext(
                context()
                        .withRequest(fullRequest())
                        .withToolSpecifications(List.of(
                                ToolSpecification.builder()
                                        .name(DocumentToolNames.GET_DOCUMENT_SNAPSHOT)
                                        .description("get full document")
                                        .build()
                        ))
        );

        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains(DocumentToolNames.GET_DOCUMENT_SNAPSHOT));
        assertTrue(systemMessage.text().contains("## Current Document Content"));
        assertTrue(systemMessage.text().contains("# Intro\n\nbody"));
        assertTrue(!systemMessage.text().contains("## Document Model"));
        assertTrue(!systemMessage.text().contains("## Document Structure JSON"));
        assertTrue(!systemMessage.text().contains("nodeId"));
        assertTrue(!systemMessage.text().contains("Use " + DocumentToolNames.READ_DOCUMENT_NODE + " to read the relevant node or block before editing."));
        assertTrue(!systemMessage.text().contains("Use " + DocumentToolNames.PATCH_DOCUMENT_NODE + " to update only the sections you inspected."));
    }

    private AgentRunContext context() {
        GroundedWriterAgentContextFactory factory = new GroundedWriterAgentContextFactory(NoOpMemoryCompressors.noop());
        return factory.prepareInitialContext(new com.agent.editor.agent.task.TaskRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "# Intro\n\nbody"),
                "rewrite the answer using available evidence",
                3,
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("older writer turn")
                ))
        )).withRequest(incrementalRequest());
    }

    private ExecutionRequest incrementalRequest() {
        ExecutionRequest request = new ExecutionRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "# Intro\n\nbody"),
                "rewrite the answer using available evidence",
                3
        );
        request.setDocumentToolMode(DocumentToolMode.INCREMENTAL);
        return request;
    }

    private ExecutionRequest fullRequest() {
        ExecutionRequest request = new ExecutionRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "# Intro\n\nbody"),
                "rewrite the answer using available evidence",
                3
        );
        request.setDocumentToolMode(DocumentToolMode.FULL);
        return request;
    }
}
