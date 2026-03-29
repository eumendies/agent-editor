package com.agent.editor.agent.v2.planning;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.PlanResult;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.task.TaskRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningAgentContextFactoryTest {

    @Test
    void shouldPrepareInitialPlanningContextFromUserTask() {
        PlanningAgentContextFactory factory = new PlanningAgentContextFactory();

        AgentRunContext context = factory.prepareInitialContext(new TaskRequest(
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

        ChatTranscriptMemory memory = assertInstanceOf(ChatTranscriptMemory.class, context.getMemory());
        assertEquals(2, memory.getMessages().size());
        assertEquals("previous turn", memory.getMessages().get(0).getText());
        assertEquals("Improve document", memory.getMessages().get(1).getText());
    }

    @Test
    void shouldPrepareExecutionStepContextByAppendingStepInstruction() {
        PlanningAgentContextFactory factory = new PlanningAgentContextFactory();
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
        assertTrue(memory.getMessages().stream().anyMatch(message -> "completed Add outline".equals(message.getText())));
        assertEquals("Plan step 2: Refine tone", memory.getMessages().get(memory.getMessages().size() - 1).getText());
    }

    @Test
    void shouldPrepareExecutionBaseContextWithoutInjectingOriginalTaskInstruction() {
        PlanningAgentContextFactory factory = new PlanningAgentContextFactory();

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
        PlanningAgentContextFactory factory = new PlanningAgentContextFactory();
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
}
