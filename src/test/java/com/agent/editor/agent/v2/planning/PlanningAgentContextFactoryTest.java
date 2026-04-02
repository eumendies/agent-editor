package com.agent.editor.agent.v2.planning;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.PlanResult;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.MemoryCompressionResult;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.support.NoOpMemoryCompressors;
import com.agent.editor.agent.v2.task.TaskRequest;
import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningAgentContextFactoryTest {

    @Test
    void shouldPrepareInitialPlanningContextFromUserTask() {
        PlanningAgentContextFactory factory = new PlanningAgentContextFactory(NoOpMemoryCompressors.noop());
        ChatTranscriptMemory sessionMemory = new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("previous turn")
        ));
        sessionMemory.setLastObservedTotalTokens(222);

        AgentRunContext context = factory.prepareInitialContext(new TaskRequest(
                "task-1",
                "session-1",
                AgentType.PLANNING,
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve document",
                3,
                sessionMemory
        ));

        ChatTranscriptMemory memory = assertInstanceOf(ChatTranscriptMemory.class, context.getMemory());
        assertEquals(2, memory.getMessages().size());
        assertEquals("previous turn", memory.getMessages().get(0).getText());
        assertEquals("Improve document", memory.getMessages().get(1).getText());
        assertEquals(222, memory.getLastObservedTotalTokens());
    }

    @Test
    void shouldPrepareExecutionStepContextByAppendingStepInstruction() {
        PlanningAgentContextFactory factory = new PlanningAgentContextFactory(NoOpMemoryCompressors.noop());
        AgentRunContext currentState = new AgentRunContext(
                null,
                1,
                "body -> outline",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("previous turn"),
                        new ChatMessage.UserChatMessage("completed Add outline")
                )),
                ExecutionStage.COMPLETED,
                null,
                List.of()
        );

        AgentRunContext stepContext = factory.prepareExecutionStepContext(currentState, new PlanResult().new PlanStep(2, "Refine tone"));

        assertEquals("body -> outline", stepContext.getCurrentContent());
        assertEquals(ExecutionStage.RUNNING, stepContext.getStage());
        ChatTranscriptMemory memory = assertInstanceOf(ChatTranscriptMemory.class, stepContext.getMemory());
        assertEquals(3, memory.getMessages().size());
        assertEquals("previous turn", memory.getMessages().get(0).getText());
        assertEquals("completed Add outline", memory.getMessages().get(1).getText());
        assertEquals("Plan step 2: Refine tone", memory.getMessages().get(2).getText());
    }

    @Test
    void shouldPrepareExecutionBaseContextWithoutInjectingOriginalTaskInstruction() {
        PlanningAgentContextFactory factory = new PlanningAgentContextFactory(NoOpMemoryCompressors.noop());

        AgentRunContext stepContext = factory.prepareExecutionInitialContext(new TaskRequest(
                "task-1",
                "session-1",
                AgentType.PLANNING,
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve document",
                3,
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("previous turn")
                ))
        ));

        ChatTranscriptMemory memory = assertInstanceOf(ChatTranscriptMemory.class, stepContext.getMemory());
        assertEquals(1, memory.getMessages().size());
        assertEquals("previous turn", memory.getMessages().get(0).getText());
        assertFalse(memory.getMessages().stream().anyMatch(message -> "Improve document".equals(message.getText())));
    }

    @Test
    void shouldSummarizeCompletedStepWithoutLeakingToolTranscript() {
        PlanningAgentContextFactory factory = new PlanningAgentContextFactory(NoOpMemoryCompressors.noop());
        AgentRunContext stepContext = new AgentRunContext(
                null,
                1,
                "body -> outline",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("Plan step 1: Add outline")
                )),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );
        ExecutionResult<String> result = new ExecutionResult<>(
                "done",
                "completed Add outline",
                "body -> outline",
                new AgentRunContext(
                        null,
                        2,
                        "body -> outline",
                        new ChatTranscriptMemory(List.of(
                                new ChatMessage.AiToolCallChatMessage("need tool", List.of()),
                                new ChatMessage.ToolExecutionResultChatMessage("tool-1", "searchContent", "{}", "tool result"),
                                new ChatMessage.AiChatMessage("completed Add outline")
                        )),
                        ExecutionStage.COMPLETED,
                        null,
                        List.of()
                )
        );

        AgentRunContext nextState = factory.summarizeCompletedStep(stepContext, result);

        assertEquals("body -> outline", nextState.getCurrentContent());
        ChatTranscriptMemory memory = assertInstanceOf(ChatTranscriptMemory.class, nextState.getMemory());
        assertTrue(memory.getMessages().stream().anyMatch(message -> "Plan step 1: Add outline".equals(message.getText())));
        assertTrue(memory.getMessages().stream().anyMatch(message ->
                message instanceof ChatMessage.AiChatMessage aiMessage
                        && "Step result: completed Add outline".equals(aiMessage.getText())
        ));
        assertFalse(memory.getMessages().stream().anyMatch(ChatMessage.AiToolCallChatMessage.class::isInstance));
        assertFalse(memory.getMessages().stream().anyMatch(ChatMessage.ToolExecutionResultChatMessage.class::isInstance));
    }

    @Test
    void shouldBuildPlanningInvocationContextWithoutCompressingAgain() {
        AtomicInteger compressionCalls = new AtomicInteger();
        PlanningAgentContextFactory factory = new PlanningAgentContextFactory(
                new com.agent.editor.agent.v2.mapper.ExecutionMemoryChatMessageMapper(),
                request -> {
                    compressionCalls.incrementAndGet();
                    return new MemoryCompressionResult(
                            new ChatTranscriptMemory(List.of(new ChatMessage.AiChatMessage("compressed planning memory"))),
                            true,
                            "compressed"
                    );
                }
        );

        var invocationContext = factory.buildModelInvocationContext(new AgentRunContext(
                null,
                0,
                "body",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.AiChatMessage("existing compressed planning memory"),
                        new ChatMessage.UserChatMessage("Improve document")
                )),
                ExecutionStage.RUNNING,
                null,
                List.of()
        ));

        assertEquals(2, invocationContext.getMessages().size());
        AiMessage message = assertInstanceOf(AiMessage.class, invocationContext.getMessages().get(0));
        assertEquals("existing compressed planning memory", message.text());
        assertEquals("Improve document", assertInstanceOf(
                dev.langchain4j.data.message.UserMessage.class,
                invocationContext.getMessages().get(1)
        ).singleText());
        assertEquals(0, compressionCalls.get());
    }
}
