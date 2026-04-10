package com.agent.editor.agent.reflexion;

import com.agent.editor.agent.core.agent.AgentType;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.core.memory.MemoryCompressionResult;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.runtime.ExecutionRequest;
import com.agent.editor.agent.core.state.DocumentSnapshot;
import com.agent.editor.agent.core.state.ExecutionStage;
import com.agent.editor.agent.support.NoOpMemoryCompressors;
import com.agent.editor.agent.task.TaskRequest;
import com.agent.editor.agent.tool.document.DocumentToolMode;
import com.agent.editor.agent.tool.document.DocumentToolNames;
import com.agent.editor.agent.tool.memory.MemoryToolNames;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflexionCriticContextFactoryTest {

    @Test
    void shouldPrepareReviewContextFromActorStateAndSummary() {
        ReflexionCriticContextFactory factory = com.agent.editor.testsupport.AgentTestFixtures.reflexionCriticContextFactory(NoOpMemoryCompressors.noop());
        TaskRequest request = new TaskRequest(
                "task-1",
                "session-1",
                AgentType.REFLEXION,
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve the draft",
                3
        );
        AgentRunContext actorState = new AgentRunContext(
                fullRequest(
                        "task-1",
                        "session-1",
                        AgentType.REFLEXION,
                        new DocumentSnapshot("doc-1", "Title", "body"),
                        "Improve the draft",
                        3
                ),
                2,
                "updated body",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("Improve the draft"),
                        new ChatMessage.AiChatMessage("actor updated draft")
                )),
                ExecutionStage.COMPLETED,
                null,
                List.of()
        );

        AgentRunContext criticContext = factory.prepareReviewContext(request, actorState, "actor summary");

        assertEquals(0, criticContext.getIteration());
        assertEquals("updated body", criticContext.getCurrentContent());
        ChatTranscriptMemory memory = assertInstanceOf(ChatTranscriptMemory.class, criticContext.getMemory());
        assertEquals(2, memory.getMessages().size());
        assertEquals("Improve the draft", memory.getMessages().get(0).getText());
        assertTrue(memory.getMessages().get(1).getText().contains("Actor Summary:"));
        assertTrue(memory.getMessages().get(1).getText().contains("Actor Summary:"));
        assertTrue(memory.getMessages().get(1).getText().contains("actor summary"));
    }

    @Test
    void shouldPreserveObservedTokensWhenPreparingInitialContext() {
        ReflexionCriticContextFactory factory = com.agent.editor.testsupport.AgentTestFixtures.reflexionCriticContextFactory(NoOpMemoryCompressors.noop());
        ChatTranscriptMemory sessionMemory = new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("previous turn")
        ));
        sessionMemory.setLastObservedTotalTokens(123);

        AgentRunContext context = factory.prepareInitialContext(new TaskRequest(
                "task-0",
                "session-0",
                AgentType.REFLEXION,
                new DocumentSnapshot("doc-0", "Title", "body"),
                "Improve the draft",
                3,
                sessionMemory
        ));

        ChatTranscriptMemory memory = assertInstanceOf(ChatTranscriptMemory.class, context.getMemory());
        assertEquals(2, memory.getMessages().size());
        assertEquals(123, memory.getLastObservedTotalTokens());
    }

    @Test
    void shouldBuildAnalysisInvocationContextWithTranscriptAndVisibleTools() {
        ReflexionCriticContextFactory factory = com.agent.editor.testsupport.AgentTestFixtures.reflexionCriticContextFactory(NoOpMemoryCompressors.noop());
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("analyzeDocument")
                .description("analyze document")
                .build();

        AgentRunContext context = new AgentRunContext(
                fullRequest(
                        "task-2",
                        "session-2",
                        AgentType.REFLEXION,
                        new DocumentSnapshot("doc-1", "Title", "Draft body"),
                        "Review this draft and decide pass or revise",
                        3
                ),
                0,
                "Draft body",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("Improve the draft"),
                        new ChatMessage.ToolExecutionResultChatMessage(
                                "tool-1",
                                "analyzeDocument",
                                "{\"focus\":\"intro\"}",
                                "intro is too long"
                        )
                )),
                ExecutionStage.RUNNING,
                null,
                List.of(toolSpecification)
        );

        var invocationContext = factory.buildModelInvocationContext(context);

        assertNull(invocationContext.getResponseFormat());
        assertEquals(List.of(toolSpecification), invocationContext.getToolSpecifications());
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains("## Role"));
        assertTrue(systemMessage.text().contains("## Workflow"));
        assertTrue(systemMessage.text().contains("## Long-Term Memory Rules"));
        assertTrue(systemMessage.text().contains(MemoryToolNames.SEARCH_MEMORY));
        assertTrue(systemMessage.text().contains("DOCUMENT_DECISION"));
        assertTrue(systemMessage.text().contains("treat retrieved"));
        assertTrue(!systemMessage.text().contains(MemoryToolNames.UPSERT_MEMORY));
        assertTrue(systemMessage.text().contains("## Output Rules"));
        assertTrue(invocationContext.getMessages().stream().anyMatch(message -> message.toString().contains("intro is too long")));
    }

    @Test
    void shouldDescribeIncrementalReviewToolsWhenNodeReadIsVisible() {
        ReflexionCriticContextFactory factory = com.agent.editor.testsupport.AgentTestFixtures.reflexionCriticContextFactory(NoOpMemoryCompressors.noop());
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name(DocumentToolNames.READ_DOCUMENT_NODE)
                .description("read one node")
                .build();

        var invocationContext = factory.buildModelInvocationContext(new AgentRunContext(
                incrementalRequest(
                        "task-5",
                        "session-5",
                        AgentType.REFLEXION,
                        new DocumentSnapshot("doc-1", "Title", "# Intro\n\nbody"),
                        "Review this draft and decide pass or revise",
                        3
                ),
                0,
                "# Intro\n\nbody",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("Improve the draft")
                )),
                ExecutionStage.RUNNING,
                null,
                List.of(toolSpecification)
        ));

        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains(DocumentToolNames.READ_DOCUMENT_NODE));
        assertTrue(!systemMessage.text().contains(DocumentToolNames.GET_DOCUMENT_SNAPSHOT));
    }

    @Test
    void shouldEmbedCurrentDocumentContentWhenFullReviewToolsAreVisible() {
        ReflexionCriticContextFactory factory = com.agent.editor.testsupport.AgentTestFixtures.reflexionCriticContextFactory(NoOpMemoryCompressors.noop());
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name(DocumentToolNames.GET_DOCUMENT_SNAPSHOT)
                .description("read full document")
                .build();

        var invocationContext = factory.buildModelInvocationContext(new AgentRunContext(
                fullRequest(
                        "task-6",
                        "session-6",
                        AgentType.REFLEXION,
                        new DocumentSnapshot("doc-1", "Title", "Draft body"),
                        "Review this draft and decide pass or revise",
                        3
                ),
                0,
                "Draft body",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("Improve the draft")
                )),
                ExecutionStage.RUNNING,
                null,
                List.of(toolSpecification)
        ));

        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains("## Current Document Content"));
        assertTrue(systemMessage.text().contains("Draft body"));
        assertTrue(!systemMessage.text().contains("## Document Structure JSON"));
        assertTrue(!systemMessage.text().contains("nodeId"));
    }

    @Test
    void shouldBuildStrictJsonFinalizationInvocationWithoutTools() {
        ReflexionCriticContextFactory factory = com.agent.editor.testsupport.AgentTestFixtures.reflexionCriticContextFactory(NoOpMemoryCompressors.noop());
        AgentRunContext context = new AgentRunContext(
                fullRequest(
                        "task-3",
                        "session-3",
                        AgentType.REFLEXION,
                        new DocumentSnapshot("doc-1", "Title", "Draft body"),
                        "Review this draft and decide pass or revise",
                        3
                ),
                0,
                "Draft body",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("Improve the draft")
                )),
                ExecutionStage.RUNNING,
                null,
                List.of(ToolSpecification.builder().name("analyzeDocument").description("analyze").build())
        );

        var invocationContext = factory.buildFinalizationInvocationContext(context, "free-form analysis");

        assertTrue(invocationContext.getToolSpecifications().isEmpty());
        assertEquals(ResponseFormatType.JSON, invocationContext.getResponseFormat().type());
        assertEquals("reflexion_critique", invocationContext.getResponseFormat().jsonSchema().name());
        JsonObjectSchema rootSchema = assertInstanceOf(
                JsonObjectSchema.class,
                invocationContext.getResponseFormat().jsonSchema().rootElement()
        );
        assertEquals(List.of("verdict", "feedback", "reasoning"), rootSchema.required());
        UserMessage finalizationPrompt = assertInstanceOf(
                UserMessage.class,
                invocationContext.getMessages().get(invocationContext.getMessages().size() - 1)
        );
        assertTrue(finalizationPrompt.singleText().contains("free-form analysis"));
        assertTrue(!finalizationPrompt.singleText().contains("Current document:"));
    }

    @Test
    void shouldBuildAnalysisInvocationContextWithoutCompressingAgain() {
        AtomicInteger compressionCalls = new AtomicInteger();
        ReflexionCriticContextFactory factory = new ReflexionCriticContextFactory(
                new com.agent.editor.agent.mapper.ExecutionMemoryChatMessageMapper(),
                request -> {
                    compressionCalls.incrementAndGet();
                    return new MemoryCompressionResult(
                            new ChatTranscriptMemory(List.of(new ChatMessage.AiChatMessage("compressed critic memory"))),
                            true,
                            "compressed"
                    );
                },
                com.agent.editor.testsupport.AgentTestFixtures.structuredDocumentService()
        );

        var invocationContext = factory.buildModelInvocationContext(new AgentRunContext(
                null,
                0,
                "Draft body",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.AiChatMessage("existing compressed critic memory")
                )),
                ExecutionStage.RUNNING,
                null,
                List.of()
        ));

        assertEquals(2, invocationContext.getMessages().size());
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains("critic for a document editing reflexion workflow"));
        AiMessage message = assertInstanceOf(AiMessage.class, invocationContext.getMessages().get(1));
        assertEquals("existing compressed critic memory", message.text());
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
