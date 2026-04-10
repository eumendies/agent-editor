package com.agent.editor.agent.reflexion;

import com.agent.editor.agent.core.agent.AgentType;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.state.DocumentSnapshot;
import com.agent.editor.agent.core.state.ExecutionStage;
import com.agent.editor.agent.task.TaskRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflexionActorContextFactoryTest {

    @Test
    void shouldPrepareRevisionContextByAppendingStructuredCritiqueMessage() {
        ReflexionActorContextFactory factory = com.agent.editor.testsupport.AgentTestFixtures.reflexionActorContextFactory(com.agent.editor.agent.support.NoOpMemoryCompressors.noop());
        AgentRunContext actorState = new AgentRunContext(
                null,
                1,
                "updated body",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("Improve the draft")
                )),
                ExecutionStage.COMPLETED,
                null,
                List.of()
        );

        AgentRunContext nextRoundContext = factory.prepareRevisionContext(
                new TaskRequest(
                        "task-1",
                        "session-1",
                        AgentType.REFLEXION,
                        new DocumentSnapshot("doc-1", "Title", "body"),
                        "Improve the draft",
                        3
                ),
                actorState,
                2,
                new ReflexionCritique(
                        ReflexionVerdict.REVISE,
                        "Tighten the introduction",
                        "too long"
                )
        );

        assertEquals(ExecutionStage.RUNNING, nextRoundContext.getStage());
        ChatTranscriptMemory memory = assertInstanceOf(ChatTranscriptMemory.class, nextRoundContext.getMemory());
        assertEquals(2, memory.getMessages().size());
        assertEquals("Improve the draft", memory.getMessages().get(0).getText());
        assertTrue(memory.getMessages().get(1).getText().contains("\"round\":2"));
        assertTrue(memory.getMessages().get(1).getText().contains("\"verdict\":\"REVISE\""));
        assertTrue(memory.getMessages().get(1).getText().contains("Tighten the introduction"));
    }
}
