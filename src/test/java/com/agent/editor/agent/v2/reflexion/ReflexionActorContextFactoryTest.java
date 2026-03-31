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
        ReflexionActorContextFactory factory = new ReflexionActorContextFactory(request -> new com.agent.editor.agent.v2.core.memory.MemoryCompressionResult(
                new ChatTranscriptMemory(List.of(new ChatMessage.AiChatMessage("compressed revision context"))),
                true,
                "compressed"
        ));
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
        assertEquals(1, memory.getMessages().size());
        assertEquals("compressed revision context", memory.getMessages().get(0).getText());
    }
}
