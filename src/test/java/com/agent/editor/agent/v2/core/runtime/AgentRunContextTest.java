package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
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

        assertSame(request, context.getRequest());
        assertEquals(2, context.getIteration());
        assertEquals("draft body", context.getCurrentContent());
        assertSame(memory, context.getMemory());
        assertEquals(ExecutionStage.RUNNING, context.getStage());
        assertTrue(context.getToolSpecifications().isEmpty());
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

        assertEquals(1, updated.getIteration());
        assertEquals("final draft", updated.getCurrentContent());
        assertEquals(ExecutionStage.COMPLETED, updated.getStage());
        ChatTranscriptMemory memory = (ChatTranscriptMemory) updated.getMemory();
        assertEquals(1, memory.getMessages().size());
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
