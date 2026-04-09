package com.agent.editor.agent.supervisor.worker;

import com.agent.editor.agent.core.agent.AgentType;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.core.state.DocumentSnapshot;
import com.agent.editor.agent.support.NoOpMemoryCompressors;
import com.agent.editor.agent.tool.document.DocumentToolNames;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearcherAgentContextFactoryTest {

    @Test
    void shouldBuildInvocationContextFromTranscriptIncludingCurrentInstruction() {
        AtomicInteger compressionCalls = new AtomicInteger();
        ResearcherAgentContextFactory factory = new ResearcherAgentContextFactory(request -> {
            compressionCalls.incrementAndGet();
            return new com.agent.editor.agent.core.memory.MemoryCompressionResult(
                    new ChatTranscriptMemory(List.of(
                            new ChatMessage.UserChatMessage("older researcher turn"),
                            new ChatMessage.UserChatMessage("ground this answer")
                    )),
                    true,
                    "compressed"
            );
        });

        var invocationContext = factory.buildModelInvocationContext(context());

        assertEquals(3, invocationContext.getMessages().size());
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains("## Role"));
        assertTrue(systemMessage.text().contains("## Workflow"));
        assertTrue(systemMessage.text().contains("## Output Rules"));
        assertTrue(systemMessage.text().contains("researcher worker"));
        assertTrue(systemMessage.text().contains("Use " + DocumentToolNames.RETRIEVE_KNOWLEDGE));
        assertTrue(systemMessage.text().contains("rewrite the query"));
        assertTrue(systemMessage.text().contains("multiple " + DocumentToolNames.RETRIEVE_KNOWLEDGE + " tool calls"));
        assertTrue(systemMessage.text().contains("ResearcherSummary"));
        assertTrue(systemMessage.text().contains("\"evidenceSummary\": \"string\""));
        assertTrue(systemMessage.text().contains("\"limitations\": \"string\""));
        assertTrue(systemMessage.text().contains("\"uncoveredPoints\": [\"string\"]"));
        UserMessage olderTurnMessage = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(1));
        assertEquals("older researcher turn", olderTurnMessage.singleText());
        UserMessage currentTurnMessage = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(2));
        assertEquals("ground this answer", currentTurnMessage.singleText());
        assertEquals(0, compressionCalls.get());
    }

    @Test
    void shouldPrepareInitialContextByAppendingCurrentInstructionToTranscript() {
        ResearcherAgentContextFactory factory = new ResearcherAgentContextFactory(NoOpMemoryCompressors.noop());

        AgentRunContext context = factory.prepareInitialContext(new com.agent.editor.agent.task.TaskRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "ground this answer",
                3,
                new ChatTranscriptMemory(List.of(new ChatMessage.UserChatMessage("raw researcher memory")))
        ));

        ChatTranscriptMemory memory = assertInstanceOf(ChatTranscriptMemory.class, context.getMemory());
        assertEquals(2, memory.getMessages().size());
        assertEquals("raw researcher memory", memory.getMessages().get(0).getText());
        assertEquals("ground this answer", memory.getMessages().get(1).getText());
    }

    private AgentRunContext context() {
        ResearcherAgentContextFactory factory = new ResearcherAgentContextFactory(NoOpMemoryCompressors.noop());
        return factory.prepareInitialContext(new com.agent.editor.agent.task.TaskRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "ground this answer",
                3,
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("older researcher turn")
                ))
        ));
    }
}
