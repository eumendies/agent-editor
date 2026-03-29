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

class EvidenceReviewerAgentContextFactoryTest {

    @Test
    void shouldBuildInvocationContextWithInstructionAndTranscript() {
        EvidenceReviewerAgentContextFactory factory = new EvidenceReviewerAgentContextFactory();

        var invocationContext = factory.buildModelInvocationContext(context());

        assertEquals(4, invocationContext.getMessages().size());
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains("reviewer worker"));
        assertTrue(systemMessage.text().contains("ReviewerFeedback"));
        assertTrue(systemMessage.text().contains("Do not wrap JSON in markdown fences or backticks."));
        UserMessage instructionMessage = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(1));
        assertEquals("review whether the answer follows the instruction and evidence", instructionMessage.singleText());
        UserMessage memoryMessage = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(2));
        assertEquals("review summary", memoryMessage.singleText());
    }

    private AgentRunContext context() {
        return new AgentRunContext(
                new ExecutionRequest(
                        "task-1",
                        "session-1",
                        AgentType.REACT,
                        new DocumentSnapshot("doc-1", "title", "body"),
                        "review whether the answer follows the instruction and evidence",
                        3
                ),
                0,
                "body",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("review summary"),
                        new ChatMessage.AiChatMessage("reviewer scratchpad")
                )),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );
    }
}
