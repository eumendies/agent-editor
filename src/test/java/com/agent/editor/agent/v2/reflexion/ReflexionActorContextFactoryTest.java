package com.agent.editor.agent.v2.reflexion;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.task.TaskRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflexionActorContextFactoryTest {

    @Test
    void shouldPrepareRevisionContextByAppendingStructuredCritiqueMessage() {
        ReflexionActorContextFactory factory = new ReflexionActorContextFactory();
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
        assertTrue(memory.getMessages().stream().anyMatch(message ->
                message instanceof ChatMessage.UserChatMessage userMessage
                        && userMessage.getText().contains("\"round\":2")
                        && userMessage.getText().contains("\"verdict\":\"REVISE\"")
                        && userMessage.getText().contains("\"feedback\":\"Tighten the introduction\"")
        ));
    }
}
