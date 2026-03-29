package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundedWriterAgentContextFactoryTest {

    @Test
    void shouldBuildInvocationContextWithInstructionBeforeTranscript() {
        GroundedWriterAgentContextFactory factory = new GroundedWriterAgentContextFactory();

        var invocationContext = factory.buildModelInvocationContext(context());

        assertEquals(4, invocationContext.getMessages().size());
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains("grounded writer worker"));
        UserMessage instructionMessage = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(1));
        assertEquals("rewrite the answer using available evidence", instructionMessage.singleText());
        UserMessage memoryMessage = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(2));
        assertEquals("existing summary", memoryMessage.singleText());
    }

    private AgentRunContext context() {
        return new AgentRunContext(
                new ExecutionRequest(
                        "task-1",
                        "session-1",
                        AgentType.REACT,
                        new DocumentSnapshot("doc-1", "title", "body"),
                        "rewrite the answer using available evidence",
                        3
                ),
                0,
                "body",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("existing summary"),
                        new ChatMessage.AiChatMessage("writer scratchpad")
                )),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );
    }
}
