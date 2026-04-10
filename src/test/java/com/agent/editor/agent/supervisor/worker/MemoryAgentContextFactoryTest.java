package com.agent.editor.agent.supervisor.worker;

import com.agent.editor.agent.core.agent.AgentType;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.core.runtime.ExecutionRequest;
import com.agent.editor.agent.core.state.DocumentSnapshot;
import com.agent.editor.agent.support.NoOpMemoryCompressors;
import com.agent.editor.agent.tool.memory.MemoryToolNames;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryAgentContextFactoryTest {

    @Test
    void shouldBuildInvocationContextFromTranscriptIncludingCurrentInstruction() {
        AtomicInteger compressionCalls = new AtomicInteger();
        MemoryAgentContextFactory factory = new MemoryAgentContextFactory(
                com.agent.editor.testsupport.AgentTestFixtures.memoryChatMessageMapper(),
                request -> {
            compressionCalls.incrementAndGet();
            return new com.agent.editor.agent.core.memory.MemoryCompressionResult(
                    new ChatTranscriptMemory(List.of(
                            new ChatMessage.UserChatMessage("older memory turn"),
                            new ChatMessage.UserChatMessage("retrieve prior document constraints and update durable decisions")
                    )),
                    true,
                    "compressed"
            );
        });

        var invocationContext = factory.buildModelInvocationContext(context());

        assertEquals(3, invocationContext.getMessages().size());
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains("memory worker"));
        assertTrue(systemMessage.text().contains("DOCUMENT_DECISION"));
        assertTrue(systemMessage.text().contains("Never write USER_PROFILE"));
        assertTrue(systemMessage.text().contains("rule-style"));
        assertTrue(systemMessage.text().contains("replace/delete"));
        assertTrue(systemMessage.text().contains(MemoryToolNames.SEARCH_MEMORY));
        assertTrue(systemMessage.text().contains(MemoryToolNames.UPSERT_MEMORY));
        assertTrue(systemMessage.text().contains("MemoryWorkerSummary"));
        assertTrue(systemMessage.text().contains("doc-1"));
        assertTrue(systemMessage.text().contains("title"));
        assertTrue(systemMessage.text().contains("body"));
        UserMessage historyMessage = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(1));
        assertEquals("older memory turn", historyMessage.singleText());
        UserMessage currentTurnMessage = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(2));
        assertEquals("retrieve prior document constraints and update durable decisions", currentTurnMessage.singleText());
        assertEquals(0, compressionCalls.get());
    }

    @Test
    void shouldPrepareInitialContextByAppendingCurrentInstructionToTranscript() {
        MemoryAgentContextFactory factory = com.agent.editor.testsupport.AgentTestFixtures.memoryAgentContextFactory(NoOpMemoryCompressors.noop());

        AgentRunContext context = factory.prepareInitialContext(new com.agent.editor.agent.task.TaskRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "retrieve prior document constraints and update durable decisions",
                3,
                new ChatTranscriptMemory(List.of(new ChatMessage.UserChatMessage("raw memory history")))
        ));

        ChatTranscriptMemory memory = assertInstanceOf(ChatTranscriptMemory.class, context.getMemory());
        assertEquals(2, memory.getMessages().size());
        assertEquals("raw memory history", memory.getMessages().get(0).getText());
        assertEquals("retrieve prior document constraints and update durable decisions", memory.getMessages().get(1).getText());
    }

    private AgentRunContext context() {
        MemoryAgentContextFactory factory = com.agent.editor.testsupport.AgentTestFixtures.memoryAgentContextFactory(NoOpMemoryCompressors.noop());
        return factory.prepareInitialContext(new com.agent.editor.agent.task.TaskRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "retrieve prior document constraints and update durable decisions",
                3,
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("older memory turn")
                ))
        )).withRequest(fullRequest());
    }

    private ExecutionRequest fullRequest() {
        return new ExecutionRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "retrieve prior document constraints and update durable decisions",
                3
        );
    }
}
