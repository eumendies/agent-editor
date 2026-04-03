package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.support.NoOpMemoryCompressors;
import com.agent.editor.agent.v2.tool.document.DocumentToolNames;
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
            return new com.agent.editor.agent.v2.core.memory.MemoryCompressionResult(
                    new ChatTranscriptMemory(List.of(
                            new ChatMessage.UserChatMessage("older writer turn"),
                            new ChatMessage.UserChatMessage("rewrite the answer using available evidence")
                    )),
                    true,
                    "compressed"
            );
        });

        var invocationContext = factory.buildModelInvocationContext(context());

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

        AgentRunContext context = factory.prepareInitialContext(new com.agent.editor.agent.v2.task.TaskRequest(
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

    private AgentRunContext context() {
        GroundedWriterAgentContextFactory factory = new GroundedWriterAgentContextFactory(NoOpMemoryCompressors.noop());
        return factory.prepareInitialContext(new com.agent.editor.agent.v2.task.TaskRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "# Intro\n\nbody"),
                "rewrite the answer using available evidence",
                3,
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("older writer turn")
                ))
        )).withRequest(new ExecutionRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "# Intro\n\nbody"),
                "rewrite the answer using available evidence",
                3
        ));
    }
}
