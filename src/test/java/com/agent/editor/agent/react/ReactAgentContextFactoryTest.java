package com.agent.editor.agent.react;

import com.agent.editor.agent.core.agent.AgentType;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.core.memory.MemoryCompressionResult;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.runtime.ExecutionRequest;
import com.agent.editor.agent.core.state.DocumentSnapshot;
import com.agent.editor.agent.core.state.ExecutionStage;
import com.agent.editor.agent.support.NoOpMemoryCompressors;
import com.agent.editor.agent.tool.document.DocumentToolMode;
import com.agent.editor.agent.task.TaskRequest;
import com.agent.editor.agent.tool.document.DocumentToolNames;
import com.agent.editor.agent.tool.memory.MemoryToolNames;
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
        ReactAgentContextFactory factory = com.agent.editor.testsupport.AgentTestFixtures.reactAgentContextFactory(NoOpMemoryCompressors.noop());
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
        ReactAgentContextFactory factory = com.agent.editor.testsupport.AgentTestFixtures.reactAgentContextFactory(NoOpMemoryCompressors.noop());
        ToolSpecification readToolSpecification = ToolSpecification.builder()
                .name(DocumentToolNames.READ_DOCUMENT_NODE)
                .description("read document node")
                .build();
        ToolSpecification patchToolSpecification = ToolSpecification.builder()
                .name(DocumentToolNames.PATCH_DOCUMENT_NODE)
                .description("patch document node")
                .build();

        AgentRunContext context = new AgentRunContext(
                incrementalRequest(
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
                List.of(readToolSpecification, patchToolSpecification)
        );

        var invocationContext = factory.buildModelInvocationContext(context);

        assertEquals(List.of(readToolSpecification, patchToolSpecification), invocationContext.getToolSpecifications());
        assertEquals(4, invocationContext.getMessages().size());
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains("ReAct-style document editing agent"));
        assertTrue(systemMessage.text().contains(DocumentToolNames.READ_DOCUMENT_NODE));
        assertTrue(systemMessage.text().contains(DocumentToolNames.PATCH_DOCUMENT_NODE));
        assertTrue(systemMessage.text().contains("## Workflow"));
        assertTrue(systemMessage.text().contains("## Tool Rules"));
        assertTrue(systemMessage.text().contains("## Long-Term Memory Rules"));
        assertTrue(systemMessage.text().contains(MemoryToolNames.SEARCH_MEMORY));
        assertTrue(systemMessage.text().contains(MemoryToolNames.UPSERT_MEMORY));
        assertTrue(systemMessage.text().contains("DOCUMENT_DECISION"));
        assertTrue(systemMessage.text().contains("USER_PROFILE"));
        assertTrue(systemMessage.text().contains("Do not store execution logs"));
        assertTrue(systemMessage.text().contains("## Forbidden Actions"));
        assertTrue(systemMessage.text().contains("## Document Structure JSON"));
        assertTrue(systemMessage.text().contains("\"nodeId\":\"node-1\""));
        assertTrue(systemMessage.text().contains("\"headingText\":\"Intro\""));
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
    void shouldDescribeWholeDocumentWorkflowWhenOnlyFullDocumentToolsAreVisible() {
        ReactAgentContextFactory factory = com.agent.editor.testsupport.AgentTestFixtures.reactAgentContextFactory(NoOpMemoryCompressors.noop());
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name(DocumentToolNames.GET_DOCUMENT_SNAPSHOT)
                .description("read the latest full document")
                .build();

        var invocationContext = factory.buildModelInvocationContext(new AgentRunContext(
                fullRequest(
                        "task-4",
                        "session-4",
                        AgentType.REACT,
                        new DocumentSnapshot("doc-1", "Title", "# Intro\n\nbody"),
                        "rewrite this",
                        3
                ),
                1,
                "# Intro\n\nbody",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("rewrite this")
                )),
                ExecutionStage.RUNNING,
                null,
                List.of(toolSpecification)
        ));

        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains(DocumentToolNames.GET_DOCUMENT_SNAPSHOT));
        assertTrue(systemMessage.text().contains("## Current Document Content"));
        assertTrue(systemMessage.text().contains("# Intro\n\nbody"));
        assertTrue(!systemMessage.text().contains("## Document Model"));
        assertTrue(!systemMessage.text().contains("## Document Structure JSON"));
        assertTrue(!systemMessage.text().contains("nodeId"));
        assertTrue(!systemMessage.text().contains("Prefer " + DocumentToolNames.READ_DOCUMENT_NODE + " for targeted reads."));
        assertTrue(!systemMessage.text().contains("Prefer " + DocumentToolNames.PATCH_DOCUMENT_NODE + " for targeted writes."));
    }

    @Test
    void shouldIncludeConfirmedUserProfileGuidanceInSystemPrompt() {
        ReactAgentContextFactory factory = com.agent.editor.testsupport.AgentTestFixtures.reactAgentContextFactory(NoOpMemoryCompressors.noop());
        ExecutionRequest request = fullRequest(
                "task-5",
                "session-5",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "Title", "# Intro\n\nbody"),
                "rewrite this",
                3
        );
        request.setUserProfileGuidance("Confirmed user profile:\n- Always answer in Chinese");

        var invocationContext = factory.buildModelInvocationContext(new AgentRunContext(
                request,
                1,
                "# Intro\n\nbody",
                new ChatTranscriptMemory(List.of(new ChatMessage.UserChatMessage("rewrite this"))),
                ExecutionStage.RUNNING,
                null,
                List.of()
        ));

        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains("Confirmed user profile"));
        assertTrue(systemMessage.text().contains("Always answer in Chinese"));
    }

    @Test
    void shouldBuildModelInvocationContextWithoutCompressingAgain() {
        AtomicInteger compressionCalls = new AtomicInteger();
        ReactAgentContextFactory factory = new ReactAgentContextFactory(
                new com.agent.editor.agent.mapper.ExecutionMemoryChatMessageMapper(),
                request -> {
                    compressionCalls.incrementAndGet();
                    return new MemoryCompressionResult(
                            new ChatTranscriptMemory(List.of(new ChatMessage.AiChatMessage("compressed summary"))),
                            true,
                            "compressed"
                    );
                },
                com.agent.editor.testsupport.AgentTestFixtures.structuredDocumentService()
        );

        var invocationContext = factory.buildModelInvocationContext(new AgentRunContext(
                fullRequest(
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

    private ExecutionRequest incrementalRequest(String taskId,
                                                String sessionId,
                                                AgentType agentType,
                                                DocumentSnapshot document,
                                                String instruction,
                                                int maxIterations) {
        ExecutionRequest request = new ExecutionRequest(taskId, sessionId, agentType, document, instruction, maxIterations);
        request.setDocumentToolMode(DocumentToolMode.INCREMENTAL);
        return request;
    }

    private ExecutionRequest fullRequest(String taskId,
                                         String sessionId,
                                         AgentType agentType,
                                         DocumentSnapshot document,
                                         String instruction,
                                         int maxIterations) {
        ExecutionRequest request = new ExecutionRequest(taskId, sessionId, agentType, document, instruction, maxIterations);
        request.setDocumentToolMode(DocumentToolMode.FULL);
        return request;
    }
}
