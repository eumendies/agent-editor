package com.agent.editor.agent.v2.react;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.task.TaskRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactAgentContextFactoryTest {

    @Test
    void shouldPrepareInitialContextByAppendingCurrentInstructionOnce() {
        ReactAgentContextFactory factory = new ReactAgentContextFactory();

        AgentRunContext context = factory.prepareInitialContext(new TaskRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "Title", "body"),
                "rewrite this",
                3,
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("previous turn")
                ))
        ));

        ChatTranscriptMemory memory = assertInstanceOf(ChatTranscriptMemory.class, context.getMemory());
        assertEquals(2, memory.getMessages().size());
        assertEquals("previous turn", memory.getMessages().get(0).getText());
        assertEquals("rewrite this", memory.getMessages().get(1).getText());
        assertEquals("body", context.getCurrentContent());
        assertEquals(ExecutionStage.RUNNING, context.getStage());
    }

    @Test
    void shouldBuildModelInvocationContextFromTranscriptAndVisibleTools() {
        ReactAgentContextFactory factory = new ReactAgentContextFactory();
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("editDocument")
                .description("edit document")
                .build();

        AgentRunContext context = new AgentRunContext(
                new ExecutionRequest(
                        "task-2",
                        "session-2",
                        AgentType.REACT,
                        new DocumentSnapshot("doc-1", "Title", "body"),
                        "rewrite this",
                        3
                ),
                1,
                "body",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("previous turn"),
                        new ChatMessage.ToolExecutionResultChatMessage(
                                "tool-1",
                                "searchContent",
                                "{\"query\":\"intro\"}",
                                "found intro"
                        ),
                        new ChatMessage.UserChatMessage("rewrite this")
                )),
                ExecutionStage.RUNNING,
                null,
                List.of(toolSpecification)
        );

        var invocationContext = factory.buildModelInvocationContext(context);

        assertEquals(List.of(toolSpecification), invocationContext.getToolSpecifications());
        assertEquals(4, invocationContext.getMessages().size());
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains("ReAct-style document editing agent"));
        UserMessage previousTurn = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(1));
        assertEquals("previous turn", previousTurn.singleText());
        ToolExecutionResultMessage toolMessage = assertInstanceOf(
                ToolExecutionResultMessage.class,
                invocationContext.getMessages().get(2)
        );
        assertEquals("tool-1", toolMessage.id());
        UserMessage currentTurn = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(3));
        assertEquals("rewrite this", currentTurn.singleText());
    }
}
