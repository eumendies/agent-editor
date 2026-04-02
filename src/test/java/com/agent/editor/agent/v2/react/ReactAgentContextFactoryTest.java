package com.agent.editor.agent.v2.react;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.MemoryCompressionResult;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.support.NoOpMemoryCompressors;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.agent.v2.tool.document.DocumentToolNames;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactAgentContextFactoryTest {

    @Test
    void shouldPrepareInitialContextByAppendingCurrentInstructionOnce() {
        ReactAgentContextFactory factory = new ReactAgentContextFactory(NoOpMemoryCompressors.noop());
        ChatTranscriptMemory sessionMemory = new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("previous turn")
        ));
        sessionMemory.setLastObservedTotalTokens(321);

        AgentRunContext context = factory.prepareInitialContext(new TaskRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "Title", "body"),
                "rewrite this",
                3,
                sessionMemory
        ));

        ChatTranscriptMemory memory = assertInstanceOf(ChatTranscriptMemory.class, context.getMemory());
        assertEquals(2, memory.getMessages().size());
        assertEquals("previous turn", memory.getMessages().get(0).getText());
        assertEquals("rewrite this", memory.getMessages().get(1).getText());
        assertEquals(321, memory.getLastObservedTotalTokens());
        assertEquals("body", context.getCurrentContent());
        assertEquals(ExecutionStage.RUNNING, context.getStage());
    }

    @Test
    void shouldBuildModelInvocationContextFromTranscriptAndVisibleTools() {
        ReactAgentContextFactory factory = new ReactAgentContextFactory(NoOpMemoryCompressors.noop());
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("editDocument")
                .description("edit document")
                .build();

        AgentRunContext context = new AgentRunContext(
                new ExecutionRequest(
                        "task-2",
                        "session-2",
                        AgentType.REACT,
                        new DocumentSnapshot("doc-1", "Title", "# Intro\n\nbody"),
                        "rewrite this",
                        3
                ),
                1,
                "# Intro\n\nbody",
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
        assertTrue(systemMessage.text().contains(DocumentToolNames.READ_DOCUMENT_NODE));
        assertTrue(systemMessage.text().contains(DocumentToolNames.PATCH_DOCUMENT_NODE));
        assertTrue(systemMessage.text().contains("Current document structure"));
        assertTrue(systemMessage.text().contains("Intro"));
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

    @Test
    void shouldBuildModelInvocationContextWithoutCompressingAgain() {
        AtomicInteger compressionCalls = new AtomicInteger();
        ReactAgentContextFactory factory = new ReactAgentContextFactory(
                new com.agent.editor.agent.v2.mapper.ExecutionMemoryChatMessageMapper(),
                request -> {
                    compressionCalls.incrementAndGet();
                    return new MemoryCompressionResult(
                            new ChatTranscriptMemory(List.of(new ChatMessage.AiChatMessage("compressed summary"))),
                            true,
                            "compressed"
                    );
                }
        );

        var invocationContext = factory.buildModelInvocationContext(new AgentRunContext(
                new ExecutionRequest(
                        "task-3",
                        "session-3",
                        AgentType.REACT,
                        new DocumentSnapshot("doc-1", "Title", "# Intro\n\nbody"),
                        "rewrite this",
                        3
                ),
                1,
                "# Intro\n\nbody",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.AiChatMessage("already compressed summary"),
                        new ChatMessage.UserChatMessage("rewrite this")
                )),
                ExecutionStage.RUNNING,
                null,
                List.of()
        ));

        assertEquals(3, invocationContext.getMessages().size());
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains("ReAct-style document editing agent"));
        dev.langchain4j.data.message.AiMessage summaryMessage = assertInstanceOf(
                dev.langchain4j.data.message.AiMessage.class,
                invocationContext.getMessages().get(1)
        );
        assertEquals("already compressed summary", summaryMessage.text());
        UserMessage currentTurn = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(2));
        assertEquals("rewrite this", currentTurn.singleText());
        assertEquals(0, compressionCalls.get());
    }
}
