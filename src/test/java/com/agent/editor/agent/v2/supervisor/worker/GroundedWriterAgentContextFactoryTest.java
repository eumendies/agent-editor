package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.support.NoOpMemoryCompressors;
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
    void shouldBuildInvocationContextWithInstructionBeforeTranscript() {
        AtomicInteger compressionCalls = new AtomicInteger();
        GroundedWriterAgentContextFactory factory = new GroundedWriterAgentContextFactory(request -> {
            compressionCalls.incrementAndGet();
            return new com.agent.editor.agent.v2.core.memory.MemoryCompressionResult(
                    new ChatTranscriptMemory(List.of(new ChatMessage.AiChatMessage("compressed writer memory"))),
                    true,
                    "compressed"
            );
        });

        var invocationContext = factory.buildModelInvocationContext(context());

        assertEquals(3, invocationContext.getMessages().size());
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains("grounded writer worker"));
        UserMessage instructionMessage = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(1));
        assertEquals("rewrite the answer using available evidence", instructionMessage.singleText());
        UserMessage memoryMessage = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(2));
        assertEquals("already compressed writer memory", memoryMessage.singleText());
        assertEquals(0, compressionCalls.get());
    }

    @Test
    void shouldPrepareInitialContextWithCompressedMemory() {
        GroundedWriterAgentContextFactory factory = new GroundedWriterAgentContextFactory(request -> new com.agent.editor.agent.v2.core.memory.MemoryCompressionResult(
                new ChatTranscriptMemory(List.of(new ChatMessage.AiChatMessage("compressed initial writer context"))),
                true,
                "compressed"
        ));

        AgentRunContext context = factory.prepareInitialContext(new com.agent.editor.agent.v2.task.TaskRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "rewrite the answer using available evidence",
                3,
                new ChatTranscriptMemory(List.of(new ChatMessage.UserChatMessage("raw writer memory")))
        ));

        ChatTranscriptMemory memory = assertInstanceOf(ChatTranscriptMemory.class, context.getMemory());
        assertEquals(1, memory.getMessages().size());
        assertEquals("compressed initial writer context", memory.getMessages().get(0).getText());
    }

    private AgentRunContext context() {
        GroundedWriterAgentContextFactory factory = new GroundedWriterAgentContextFactory(NoOpMemoryCompressors.noop());
        return factory.prepareInitialContext(new com.agent.editor.agent.v2.task.TaskRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "rewrite the answer using available evidence",
                3,
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("already compressed writer memory")
                ))
        )).withRequest(new ExecutionRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "rewrite the answer using available evidence",
                3
        ));
    }
}
