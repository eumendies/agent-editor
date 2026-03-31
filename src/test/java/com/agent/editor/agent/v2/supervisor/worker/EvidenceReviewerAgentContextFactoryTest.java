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

class EvidenceReviewerAgentContextFactoryTest {

    @Test
    void shouldBuildInvocationContextWithInstructionAndTranscript() {
        AtomicInteger compressionCalls = new AtomicInteger();
        EvidenceReviewerAgentContextFactory factory = new EvidenceReviewerAgentContextFactory(request -> {
            compressionCalls.incrementAndGet();
            return new com.agent.editor.agent.v2.core.memory.MemoryCompressionResult(
                    new ChatTranscriptMemory(List.of(new ChatMessage.AiChatMessage("compressed reviewer memory"))),
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
        UserMessage instructionMessage = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(1));
        assertEquals("review whether the answer follows the instruction and evidence", instructionMessage.singleText());
        UserMessage memoryMessage = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(2));
        assertEquals("already compressed reviewer memory", memoryMessage.singleText());
        assertEquals(0, compressionCalls.get());
    }

    @Test
    void shouldPrepareInitialContextWithCompressedMemory() {
        EvidenceReviewerAgentContextFactory factory = new EvidenceReviewerAgentContextFactory(request -> new com.agent.editor.agent.v2.core.memory.MemoryCompressionResult(
                new ChatTranscriptMemory(List.of(new ChatMessage.AiChatMessage("compressed initial reviewer context"))),
                true,
                "compressed"
        ));

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
        assertEquals(1, memory.getMessages().size());
        assertEquals("compressed initial reviewer context", memory.getMessages().get(0).getText());
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
                        new ChatMessage.UserChatMessage("already compressed reviewer memory")
                ))
        )).withRequest(new ExecutionRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "review whether the answer follows the instruction and evidence",
                3
        ));
    }
}
