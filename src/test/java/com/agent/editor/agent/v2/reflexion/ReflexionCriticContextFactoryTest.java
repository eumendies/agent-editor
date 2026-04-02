package com.agent.editor.agent.v2.reflexion;

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
        ReflexionCriticContextFactory factory = new ReflexionCriticContextFactory(NoOpMemoryCompressors.noop());
        TaskRequest request = new TaskRequest(
                "task-1",
                "session-1",
                AgentType.REFLEXION,
                new DocumentSnapshot("doc-1", "Title", "body"),
                "Improve the draft",
                3
        );
        AgentRunContext actorState = new AgentRunContext(
                new ExecutionRequest(
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
        assertTrue(memory.getMessages().get(1).getText().contains("Current Content:"));
        assertTrue(memory.getMessages().get(1).getText().contains("updated body"));
        assertTrue(memory.getMessages().get(1).getText().contains("Actor Summary:"));
        assertTrue(memory.getMessages().get(1).getText().contains("actor summary"));
    }

    @Test
    void shouldPreserveObservedTokensWhenPreparingInitialContext() {
        ReflexionCriticContextFactory factory = new ReflexionCriticContextFactory(NoOpMemoryCompressors.noop());
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
        ReflexionCriticContextFactory factory = new ReflexionCriticContextFactory(NoOpMemoryCompressors.noop());
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("analyzeDocument")
                .description("analyze document")
                .build();

        AgentRunContext context = new AgentRunContext(
                new ExecutionRequest(
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
        assertTrue(systemMessage.text().contains("You are a critic for a document editing reflexion workflow"));
        assertTrue(invocationContext.getMessages().stream().anyMatch(message -> message.toString().contains("intro is too long")));
    }

    @Test
    void shouldBuildStrictJsonFinalizationInvocationWithoutTools() {
        ReflexionCriticContextFactory factory = new ReflexionCriticContextFactory(NoOpMemoryCompressors.noop());
        AgentRunContext context = new AgentRunContext(
                new ExecutionRequest(
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
    }

    @Test
    void shouldBuildAnalysisInvocationContextWithoutCompressingAgain() {
        AtomicInteger compressionCalls = new AtomicInteger();
        ReflexionCriticContextFactory factory = new ReflexionCriticContextFactory(
                new com.agent.editor.agent.v2.mapper.ExecutionMemoryChatMessageMapper(),
                request -> {
                    compressionCalls.incrementAndGet();
                    return new MemoryCompressionResult(
                            new ChatTranscriptMemory(List.of(new ChatMessage.AiChatMessage("compressed critic memory"))),
                            true,
                            "compressed"
                    );
                }
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
}
