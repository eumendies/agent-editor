package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRunContextTest {

    @Test
    void shouldCarryRequestRuntimeStateAndToolSpecificationsTogether() {
        ExecutionRequest request = new ExecutionRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "rewrite this",
                3
        );
        ChatTranscriptMemory memory = new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("rewrite this")
        ));

        AgentRunContext context = new AgentRunContext(
                request,
                2,
                "draft body",
                memory,
                ExecutionStage.RUNNING,
                null,
                List.of()
        );

        assertSame(request, context.request());
        assertEquals(2, context.iteration());
        assertEquals("draft body", context.currentContent());
        assertSame(memory, context.memory());
        assertEquals(ExecutionStage.RUNNING, context.stage());
        assertTrue(context.toolSpecifications().isEmpty());
    }

    @Test
    void shouldSupportRuntimeStyleStateTransitions() {
        ExecutionRequest request = new ExecutionRequest(
                "task-2",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "rewrite this",
                3
        );
        AgentRunContext context = new AgentRunContext(
                request,
                0,
                "body",
                new ChatTranscriptMemory(List.of()),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );

        AgentRunContext updated = context
                .appendMemory(new ChatMessage.UserChatMessage("step 1"))
                .withCurrentContent("draft")
                .advance("final draft")
                .markCompleted();

        assertEquals(1, updated.iteration());
        assertEquals("final draft", updated.currentContent());
        assertEquals(ExecutionStage.COMPLETED, updated.stage());
        ChatTranscriptMemory memory = (ChatTranscriptMemory) updated.memory();
        assertEquals(1, memory.messages().size());
    }

    @Test
    void shouldNotExposeInterpretationHelpers() {
        List<String> declaredMethodNames = List.of(AgentRunContext.class.getDeclaredMethods()).stream()
                .map(Method::getName)
                .toList();

        assertTrue(declaredMethodNames.stream().noneMatch("completed"::equals));
        assertTrue(declaredMethodNames.stream().noneMatch("toolResults"::equals));
    }
}
