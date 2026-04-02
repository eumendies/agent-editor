package com.agent.editor.agent.v2.reflexion;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.ToolLoopDecision;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.state.*;
import com.agent.editor.agent.v2.support.NoOpMemoryCompressors;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflexionCriticDefinitionTest {

    @Test
    void shouldParsePassCritiqueFromModelResponse() {
        ReflexionCritic definition = ReflexionCritic.blocking(
                new RecordingChatModel(ChatResponse.builder()
                        .aiMessage(AiMessage.from("""
                                {"verdict":"PASS","feedback":"Looks good","reasoning":"All key requirements are satisfied"}
                                """))
                        .build()),
                new ReflexionCriticContextFactory(NoOpMemoryCompressors.noop())
        );

        ToolLoopDecision toolLoopDecision = definition.decide(context());

        ToolLoopDecision.Complete complete = assertInstanceOf(ToolLoopDecision.Complete.class, toolLoopDecision);
        ReflexionCritique critique = assertInstanceOf(ReflexionCritique.class, complete.getResult());
        assertEquals(ReflexionVerdict.PASS, critique.getVerdict());
        assertEquals("Looks good", critique.getFeedback());
    }

    @Test
    void shouldParseReviseCritiqueFromModelResponse() {
        ReflexionCritic definition = ReflexionCritic.blocking(
                new RecordingChatModel(ChatResponse.builder()
                        .aiMessage(AiMessage.from("""
                                {"verdict":"REVISE","feedback":"Tighten the introduction","reasoning":"The opening is too long"}
                                """))
                        .build()),
                new ReflexionCriticContextFactory(NoOpMemoryCompressors.noop())
        );

        ReflexionCritique critique = assertInstanceOf(
                ReflexionCritique.class,
                assertInstanceOf(
                        ToolLoopDecision.Complete.class,
                        definition.decide(context())
                ).getResult()
        );

        assertEquals(ReflexionVerdict.REVISE, critique.getVerdict());
        assertEquals("Tighten the introduction", critique.getFeedback());
        assertEquals("The opening is too long", critique.getReasoning());
    }

    @Test
    void shouldParseCritiqueWrappedInMarkdownFence() {
        ReflexionCritic definition = ReflexionCritic.blocking(
                new RecordingChatModel(ChatResponse.builder()
                        .aiMessage(AiMessage.from("""
                                ```json
                                {"verdict":"PASS","feedback":"Looks good","reasoning":"All key requirements are satisfied"}
                                ```
                                """))
                        .build()),
                new ReflexionCriticContextFactory(NoOpMemoryCompressors.noop())
        );

        ToolLoopDecision toolLoopDecision = definition.decide(context());

        ToolLoopDecision.Complete complete = assertInstanceOf(ToolLoopDecision.Complete.class, toolLoopDecision);
        ReflexionCritique critique = assertInstanceOf(ReflexionCritique.class, complete.getResult());
        assertEquals(ReflexionVerdict.PASS, critique.getVerdict());
        assertEquals("Looks good", critique.getFeedback());
    }

    @Test
    void shouldRejectInvalidCritiquePayload() {
        ReflexionCritic definition = ReflexionCritic.blocking(
                new RecordingChatModel(ChatResponse.builder()
                        .aiMessage(AiMessage.from("""
                                {"verdict":"MAYBE","feedback":"unclear","reasoning":"invalid verdict"}
                                """))
                        .build()),
                new ReflexionCriticContextFactory(NoOpMemoryCompressors.noop())
        );

        assertThrows(IllegalArgumentException.class, () -> definition.parseCritique("""
                {"verdict":"MAYBE","feedback":"unclear","reasoning":"invalid verdict"}
                """));
    }

    @Test
    void shouldAllowStrictCritiqueParsingFromMarkdownFencedJson() {
        ReflexionCritic definition = ReflexionCritic.blocking(
                new RecordingChatModel(ChatResponse.builder().aiMessage(AiMessage.from("{}")).build()),
                new ReflexionCriticContextFactory(NoOpMemoryCompressors.noop())
        );

        ReflexionCritique critique = definition.parseCritique("""
                ```json
                {"verdict":"PASS","feedback":"Looks good","reasoning":"All key requirements are satisfied"}
                ```
                """);

        assertEquals(ReflexionVerdict.PASS, critique.getVerdict());
        assertEquals("Looks good", critique.getFeedback());
    }

    @Test
    void shouldIncludeExecutionMemoryMessagesWhenCallingCriticModel() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("""
                {"verdict":"PASS","feedback":"Looks good","reasoning":"All key requirements are satisfied"}
                """))
                .build());
        ReflexionCritic definition = ReflexionCritic.blocking(chatModel, new ReflexionCriticContextFactory(NoOpMemoryCompressors.noop()));

        definition.decide(new AgentRunContext(
                new ExecutionRequest(
                        "task-critic-2",
                        "session-critic-2",
                        AgentType.REFLEXION,
                        new DocumentSnapshot("doc-1", "title", "Draft body"),
                        "Review this draft and decide pass or revise",
                        3
                ),
                1,
                "Draft body",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.AiChatMessage("I'll inspect the evidence first."),
                        new ChatMessage.ToolExecutionResultChatMessage(
                                "tool-call-1",
                                "analyzeDocument",
                                "{}",
                                "analyzeDocument => intro is too long"
                        )
                )),
                ExecutionStage.RUNNING,
                null,
                List.of(analyzeDocumentTool())
        ));

        assertTrue(chatModel.lastRequest.messages().stream()
                .map(this::messageText)
                .anyMatch(text -> text.contains("analyzeDocument => intro is too long")));
    }

    @Test
    void shouldAllowMultipleToolCallsBeforeReturningCritique() {
        ToolExecutionRequest firstToolRequest = ToolExecutionRequest.builder()
                .id("tool-1")
                .name("analyzeDocument")
                .arguments("{\"mode\":\"structure\"}")
                .build();
        ToolExecutionRequest secondToolRequest = ToolExecutionRequest.builder()
                .id("tool-2")
                .name("searchContent")
                .arguments("{\"query\":\"introduction\"}")
                .build();
        RecordingChatModel chatModel = new RecordingChatModel(
                ChatResponse.builder().aiMessage(AiMessage.from("analyze first", List.of(firstToolRequest))).build(),
                ChatResponse.builder().aiMessage(AiMessage.from("need one more tool", List.of(secondToolRequest))).build(),
                ChatResponse.builder().aiMessage(AiMessage.from("""
                        {"verdict":"PASS","feedback":"Looks good","reasoning":"Enough evidence collected"}
                        """)).build()
        );
        ReflexionCritic definition = ReflexionCritic.blocking(chatModel, new ReflexionCriticContextFactory(NoOpMemoryCompressors.noop()));

        ToolLoopDecision firstDecision = definition.decide(context(List.of(analyzeDocumentTool(), searchContentTool()), new ChatTranscriptMemory(List.of())));
        ToolLoopDecision.ToolCalls firstToolCalls = assertInstanceOf(ToolLoopDecision.ToolCalls.class, firstDecision);
        assertEquals("analyzeDocument", firstToolCalls.getCalls().get(0).getName());

        ToolLoopDecision secondDecision = definition.decide(context(
                List.of(analyzeDocumentTool(), searchContentTool()),
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.ToolExecutionResultChatMessage("tool-1", "analyzeDocument", "{\"mode\":\"structure\"}", "analysis result")
                ))
        ));
        ToolLoopDecision.ToolCalls secondToolCalls = assertInstanceOf(ToolLoopDecision.ToolCalls.class, secondDecision);
        assertEquals("searchContent", secondToolCalls.getCalls().get(0).getName());

        ToolLoopDecision thirdDecision = definition.decide(context(
                List.of(analyzeDocumentTool(), searchContentTool()),
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.ToolExecutionResultChatMessage("tool-1", "analyzeDocument", "{\"mode\":\"structure\"}", "analysis result"),
                        new ChatMessage.ToolExecutionResultChatMessage("tool-2", "searchContent", "{\"query\":\"introduction\"}", "search result")
                ))
        ));
        ToolLoopDecision.Complete complete = assertInstanceOf(ToolLoopDecision.Complete.class, thirdDecision);
        ReflexionCritique critique = assertInstanceOf(ReflexionCritique.class, complete.getResult());
        assertEquals(ReflexionVerdict.PASS, critique.getVerdict());
        assertEquals(3, chatModel.requests().size());
        assertNull(chatModel.requests().get(0).responseFormat());
        assertNull(chatModel.requests().get(1).responseFormat());
        assertNull(chatModel.requests().get(2).responseFormat());
        assertEquals(2, chatModel.requests().get(2).toolSpecifications().size());
    }

    @Test
    void shouldRetryWithStrictJsonSchemaWhenAnalysisResponseIsNotParseable() {
        RecordingChatModel chatModel = new RecordingChatModel(
                ChatResponse.builder().aiMessage(AiMessage.from("I think this should pass")).build(),
                ChatResponse.builder().aiMessage(AiMessage.from("""
                        {"verdict":"PASS","feedback":"Looks good","reasoning":"Enough evidence collected"}
                        """)).build()
        );
        ReflexionCritic definition = ReflexionCritic.blocking(chatModel, new ReflexionCriticContextFactory(NoOpMemoryCompressors.noop()));

        ToolLoopDecision toolLoopDecision = definition.decide(context(List.of(analyzeDocumentTool()), new ChatTranscriptMemory(List.of())));

        ToolLoopDecision.Complete complete = assertInstanceOf(ToolLoopDecision.Complete.class, toolLoopDecision);
        ReflexionCritique critique = assertInstanceOf(ReflexionCritique.class, complete.getResult());
        assertEquals(ReflexionVerdict.PASS, critique.getVerdict());
        assertEquals(2, chatModel.requests().size());
        assertNull(chatModel.requests().get(0).responseFormat());
        assertNotNull(chatModel.requests().get(1).responseFormat());
        assertEquals(ResponseFormatType.JSON, chatModel.requests().get(1).responseFormat().type());
        assertNotNull(chatModel.requests().get(1).responseFormat().jsonSchema());
        assertEquals("reflexion_critique", chatModel.requests().get(1).responseFormat().jsonSchema().name());
        JsonObjectSchema rootSchema = assertInstanceOf(
                JsonObjectSchema.class,
                chatModel.requests().get(1).responseFormat().jsonSchema().rootElement()
        );
        assertEquals(List.of("verdict", "feedback", "reasoning"), rootSchema.required());
        assertTrue(rootSchema.properties().containsKey("verdict"));
        assertTrue(rootSchema.properties().containsKey("feedback"));
        assertTrue(rootSchema.properties().containsKey("reasoning"));
        assertEquals(Boolean.FALSE, rootSchema.additionalProperties());
        assertTrue(chatModel.requests().get(1).toolSpecifications().isEmpty());
    }

    private AgentRunContext context() {
        return context(List.of(), new ChatTranscriptMemory(List.of()));
    }

    private AgentRunContext context(List<ToolSpecification> toolSpecifications, ChatTranscriptMemory memory) {
        return new AgentRunContext(
                new ExecutionRequest(
                        "task-critic-1",
                        "session-critic-1",
                        AgentType.REFLEXION,
                        new DocumentSnapshot("doc-1", "title", "Draft body"),
                        "Review this draft and decide pass or revise",
                        3
                ),
                0,
                "Draft body",
                memory,
                ExecutionStage.RUNNING,
                null,
                toolSpecifications
        );
    }

    private ToolSpecification analyzeDocumentTool() {
        return ToolSpecification.builder()
                .name("analyzeDocument")
                .description("analyze document")
                .build();
    }

    private ToolSpecification searchContentTool() {
        return ToolSpecification.builder()
                .name("searchContent")
                .description("search document")
                .build();
    }

    private String messageText(dev.langchain4j.data.message.ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            return userMessage.singleText();
        }
        if (message instanceof AiMessage aiMessage) {
            return aiMessage.text();
        }
        return message.toString();
    }

    private static final class RecordingChatModel implements ChatModel {

        private final List<ChatResponse> responses;
        private int index;
        private ChatRequest lastRequest;
        private final java.util.List<ChatRequest> requests = new java.util.ArrayList<>();

        private RecordingChatModel(ChatResponse... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            this.lastRequest = request;
            this.requests.add(request);
            return responses.get(index++);
        }

        private List<ChatRequest> requests() {
            return requests;
        }
    }
}
