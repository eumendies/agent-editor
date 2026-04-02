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
import static org.junit.jupiter.api.Assertions.assertNull;
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
        ChatTranscriptMemory memory = new ChatTranscriptMemory(List.of());
        memory.setLastObservedTotalTokens(42);
        AgentRunContext context = new AgentRunContext(
                request,
                0,
                "body",
                memory,
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
        ChatTranscriptMemory updatedMemory = (ChatTranscriptMemory) updated.getMemory();
        assertEquals(1, updatedMemory.getMessages().size());
        assertEquals(42, updatedMemory.getLastObservedTotalTokens());
    }

    @Test
    void shouldNotExposeInterpretationHelpers() {
        List<String> declaredMethodNames = List.of(AgentRunContext.class.getDeclaredMethods()).stream()
                .map(Method::getName)
                .toList();

        assertTrue(declaredMethodNames.stream().noneMatch("completed"::equals));
        assertTrue(declaredMethodNames.stream().noneMatch("toolResults"::equals));
    }

    @Test
    void shouldKeepTranscriptObservedTokenMetadata() {
        ChatTranscriptMemory emptyMemory = new ChatTranscriptMemory();
        assertNull(emptyMemory.getLastObservedTotalTokens());

        ChatTranscriptMemory memory = new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("rewrite this")
        ));
        memory.setLastObservedTotalTokens(42);

        assertEquals(42, memory.getLastObservedTotalTokens());

        AgentRunContext updated = new AgentRunContext(
                null,
                0,
                "body",
                memory,
                ExecutionStage.RUNNING,
                null,
                List.of()
        ).appendMemory(new ChatMessage.AiChatMessage("done"));

        ChatTranscriptMemory appendedMemory = (ChatTranscriptMemory) updated.getMemory();
        assertEquals(42, appendedMemory.getLastObservedTotalTokens());
    }

    @Test
    void shouldReplaceRuntimeMemoryWithoutLosingOtherState() {
        ExecutionRequest request = new ExecutionRequest(
                "task-3",
                "session-9",
                AgentType.SUPERVISOR,
                new DocumentSnapshot("doc-9", "title", "draft"),
                "review",
                5
        );
        ChatTranscriptMemory originalMemory = new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("before")
        ));
        ChatTranscriptMemory compressedMemory = new ChatTranscriptMemory(List.of(
                new ChatMessage.AiChatMessage("summary")
        ));

        AgentRunContext original = new AgentRunContext(
                request,
                4,
                "current draft",
                originalMemory,
                ExecutionStage.RUNNING,
                "waiting",
                List.of()
        );

        AgentRunContext updated = original.withMemory(compressedMemory);

        assertSame(request, updated.getRequest());
        assertEquals(4, updated.getIteration());
        assertEquals("current draft", updated.getCurrentContent());
        assertSame(compressedMemory, updated.getMemory());
        assertEquals(ExecutionStage.RUNNING, updated.getStage());
        assertEquals("waiting", updated.getPendingReason());
        assertTrue(updated.getToolSpecifications().isEmpty());
    }

    @Test
    void shouldExposeTaskIdOrEmptyFromRequest() {
        ExecutionRequest request = new ExecutionRequest(
                "task-42",
                "session-42",
                AgentType.REACT,
                new DocumentSnapshot("doc-42", "title", "body"),
                "rewrite",
                3
        );

        AgentRunContext withRequest = new AgentRunContext(
                request,
                0,
                "body",
                new ChatTranscriptMemory(List.of()),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );
        AgentRunContext withoutRequest = new AgentRunContext(0, "body");

        assertEquals("task-42", withRequest.getTaskIdOrEmpty());
        assertEquals("", withoutRequest.getTaskIdOrEmpty());
    }
}
