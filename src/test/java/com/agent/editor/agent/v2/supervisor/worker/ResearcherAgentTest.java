package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.ToolLoopDecision;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.support.NoOpMemoryCompressors;
import com.agent.editor.agent.v2.tool.document.DocumentToolNames;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ResearcherAgentTest {

    @Test
    void shouldReportReactType() {
        ResearcherAgent definition = ResearcherAgent.blocking(mock(ChatModel.class), new ResearcherAgentContextFactory(NoOpMemoryCompressors.noop()));

        assertEquals(AgentType.REACT, definition.type());
    }

    @Test
    void shouldReturnDeterministicInitialRetrieveKnowledgeCall() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("{}"))
                .build());
        ResearcherAgent definition = ResearcherAgent.blocking(chatModel, new ResearcherAgentContextFactory(NoOpMemoryCompressors.noop()));

        ToolLoopDecision decision = definition.decide(context(List.of(retrieveKnowledgeTool()), new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("ground this answer")
        ))));

        ToolLoopDecision.ToolCalls toolCalls = assertInstanceOf(ToolLoopDecision.ToolCalls.class, decision);
        assertEquals(1, toolCalls.getCalls().size());
        assertEquals(DocumentToolNames.RETRIEVE_KNOWLEDGE, toolCalls.getCalls().get(0).getName());
        assertEquals("{\"query\":\"ground this answer\"}", toolCalls.getCalls().get(0).getArguments());
        assertNull(chatModel.lastRequest);
    }

    @Test
    void shouldExposeRetrieveKnowledgeOnly() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("""
                        {"queries":["agentic rag"],"evidenceSummary":"...", "limitations":"...", "uncoveredPoints":[], "chunks":[]}
                        """))
                .build());
        ResearcherAgent definition = ResearcherAgent.blocking(chatModel, new ResearcherAgentContextFactory(NoOpMemoryCompressors.noop()));

        definition.decide(context(List.of(retrieveKnowledgeTool()), new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("ground this answer"),
                new ChatMessage.ToolExecutionResultChatMessage(
                        "tool-1",
                        DocumentToolNames.RETRIEVE_KNOWLEDGE,
                        "{\"query\":\"ground this answer\"}",
                        "[{\"chunkText\":\"supports supervisor\"}]"
                ),
                new ChatMessage.UserChatMessage("ground this answer")
        ))));

        assertEquals(List.of(DocumentToolNames.RETRIEVE_KNOWLEDGE), assertThatToolNames(chatModel.lastRequest));
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, chatModel.lastRequest.messages().get(0));
        assertTrue(systemMessage.text().contains("Use " + DocumentToolNames.RETRIEVE_KNOWLEDGE));
        assertTrue(systemMessage.text().contains("multiple " + DocumentToolNames.RETRIEVE_KNOWLEDGE + " tool calls"));
        assertTrue(systemMessage.text().contains("ResearcherSummary"));
    }

    @Test
    void shouldConvertToolRequestsToToolCallDecision() {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("tool-1")
                .name(DocumentToolNames.RETRIEVE_KNOWLEDGE)
                .arguments("{\"query\":\"agentic rag\"}")
                .build();
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("need evidence", List.of(toolRequest)))
                .build());
        ResearcherAgent definition = ResearcherAgent.blocking(chatModel, new ResearcherAgentContextFactory(NoOpMemoryCompressors.noop()));

        ToolLoopDecision toolLoopDecision = definition.decide(context(List.of(retrieveKnowledgeTool()), new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("ground this answer"),
                new ChatMessage.ToolExecutionResultChatMessage(
                        "tool-1",
                        DocumentToolNames.RETRIEVE_KNOWLEDGE,
                        "{\"query\":\"ground this answer\"}",
                        "[{\"chunkText\":\"supports supervisor\"}]"
                ),
                new ChatMessage.UserChatMessage("ground this answer")
        ))));

        ToolLoopDecision.ToolCalls toolCalls = assertInstanceOf(ToolLoopDecision.ToolCalls.class, toolLoopDecision);
        assertEquals(1, toolCalls.getCalls().size());
        assertEquals(DocumentToolNames.RETRIEVE_KNOWLEDGE, toolCalls.getCalls().get(0).getName());
    }

    @Test
    void shouldAppendTranscriptMemoryMessagesToModelRequest() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("""
                        {"queries":["agentic rag"],"evidenceSummary":"...", "limitations":"...", "uncoveredPoints":[], "chunks":[]}
                        """))
                .build());
        ResearcherAgent definition = ResearcherAgent.blocking(chatModel, new ResearcherAgentContextFactory(NoOpMemoryCompressors.noop()));

        ToolLoopDecision toolLoopDecision = definition.decide(context(List.of(retrieveKnowledgeTool()), new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("initial grounding request"),
                new ChatMessage.ToolExecutionResultChatMessage(
                        "tool-1",
                        DocumentToolNames.RETRIEVE_KNOWLEDGE,
                        "{\"query\":\"agentic rag\"}",
                        "[{\"chunkText\":\"supports supervisor\"}]"
                ),
                new ChatMessage.AiChatMessage("I need one more retrieval pass."),
                new ChatMessage.UserChatMessage("ground this answer")
        ))));

        assertInstanceOf(ToolLoopDecision.Complete.class, toolLoopDecision);
        assertEquals(5, chatModel.lastRequest.messages().size());
        assertInstanceOf(SystemMessage.class, chatModel.lastRequest.messages().get(0));
        UserMessage firstUserMessage = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(1));
        assertEquals("initial grounding request", firstUserMessage.singleText());
        ToolExecutionResultMessage toolMessage = assertInstanceOf(
                ToolExecutionResultMessage.class,
                chatModel.lastRequest.messages().get(2)
        );
        assertEquals("tool-1", toolMessage.id());
        assertEquals(DocumentToolNames.RETRIEVE_KNOWLEDGE, toolMessage.toolName());
        assertInstanceOf(AiMessage.class, chatModel.lastRequest.messages().get(3));
        UserMessage currentTurn = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(4));
        assertEquals("ground this answer", currentTurn.singleText());
    }

    @Test
    void shouldAssembleEvidencePackageFromLastRetrieveKnowledgeResult() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("""
                        {"evidenceSummary":"supports supervisor", "limitations":"no metrics", "uncoveredPoints":["benchmark data"]}
                        """))
                .build());
        ResearcherAgent definition = ResearcherAgent.blocking(chatModel, new ResearcherAgentContextFactory(NoOpMemoryCompressors.noop()));

        ToolLoopDecision decision = definition.decide(context(List.of(retrieveKnowledgeTool()), new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("ground this answer"),
                new ChatMessage.ToolExecutionResultChatMessage(
                        "tool-1",
                        DocumentToolNames.RETRIEVE_KNOWLEDGE,
                        "{\"query\":\"rewritten query\"}",
                        """
                        [{"fileName":"resume.md","heading":"项目经历","chunkText":"supports supervisor"}]
                        """
                ),
                new ChatMessage.UserChatMessage("ground this answer")
        ))));

        ToolLoopDecision.Complete<?> complete = assertInstanceOf(ToolLoopDecision.Complete.class, decision);
        EvidencePackage evidencePackage = assertInstanceOf(EvidencePackage.class, complete.getResult());
        assertEquals(List.of("rewritten query"), evidencePackage.getQueries());
        assertEquals("supports supervisor", evidencePackage.getEvidenceSummary());
        assertEquals(List.of("benchmark data"), evidencePackage.getUncoveredPoints());
        assertEquals(1, evidencePackage.getChunks().size());
        assertEquals("resume.md", evidencePackage.getChunks().get(0).getFileName());
        assertEquals("项目经历", evidencePackage.getChunks().get(0).getHeading());
        assertEquals("supports supervisor", evidencePackage.getChunks().get(0).getChunkText());
    }

    @Test
    void shouldUseContextFactoryProvidedMessages() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("{}"))
                .build());
        StubResearcherContextFactory contextFactory = new StubResearcherContextFactory();
        ResearcherAgent definition = ResearcherAgent.blocking(chatModel, contextFactory);

        definition.decide(context(List.of(retrieveKnowledgeTool()), new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("ground this answer"),
                new ChatMessage.ToolExecutionResultChatMessage(
                        "tool-1",
                        DocumentToolNames.RETRIEVE_KNOWLEDGE,
                        "{\"query\":\"ground this answer\"}",
                        "[{\"chunkText\":\"supports supervisor\"}]"
                ),
                new ChatMessage.UserChatMessage("ground this answer")
        ))));

        assertEquals(1, contextFactory.buildInvocationCount);
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, chatModel.lastRequest.messages().get(0));
        assertEquals("custom researcher system", systemMessage.text());
        UserMessage userMessage = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(1));
        assertEquals("custom researcher input", userMessage.singleText());
    }

    @Test
    void shouldInvokeModelAfterInitialRetrievalResultExists() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("""
                        {"queries":["rewritten"],"evidenceSummary":"...", "limitations":"...", "uncoveredPoints":[], "chunks":[]}
                        """))
                .build());
        ResearcherAgent definition = ResearcherAgent.blocking(chatModel, new ResearcherAgentContextFactory(NoOpMemoryCompressors.noop()));

        ToolLoopDecision decision = definition.decide(context(List.of(retrieveKnowledgeTool()), new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("ground this answer"),
                new ChatMessage.ToolExecutionResultChatMessage(
                        "tool-1",
                        DocumentToolNames.RETRIEVE_KNOWLEDGE,
                        "{\"query\":\"ground this answer\"}",
                        "[{\"chunkText\":\"supports supervisor\"}]"
                ),
                new ChatMessage.UserChatMessage("ground this answer")
        ))));

        assertInstanceOf(ToolLoopDecision.Complete.class, decision);
        assertNotNull(chatModel.lastRequest);
    }

    private AgentRunContext context(List<ToolSpecification> toolSpecifications, ChatTranscriptMemory memory) {
        return new AgentRunContext(
                new ExecutionRequest(
                        "task-1",
                        "session-1",
                        AgentType.REACT,
                        new DocumentSnapshot("doc-1", "title", "body"),
                        "ground this answer",
                        3
                ),
                0,
                "body",
                memory,
                ExecutionStage.RUNNING,
                null,
                toolSpecifications
        );
    }

    private ToolSpecification retrieveKnowledgeTool() {
        return ToolSpecification.builder()
                .name(DocumentToolNames.RETRIEVE_KNOWLEDGE)
                .description("retrieve relevant knowledge")
                .build();
    }

    private List<String> assertThatToolNames(ChatRequest request) {
        return request.toolSpecifications().stream().map(ToolSpecification::name).toList();
    }

    private static final class RecordingChatModel implements ChatModel {

        private final ChatResponse response;
        private ChatRequest lastRequest;

        private RecordingChatModel(ChatResponse response) {
            this.response = response;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            this.lastRequest = request;
            return response;
        }
    }

    private static final class StubResearcherContextFactory extends ResearcherAgentContextFactory {

        private int buildInvocationCount;

        private StubResearcherContextFactory() {
            super(NoOpMemoryCompressors.noop());
        }

        @Override
        public com.agent.editor.agent.v2.core.context.ModelInvocationContext buildModelInvocationContext(AgentRunContext context) {
            buildInvocationCount++;
            return new com.agent.editor.agent.v2.core.context.ModelInvocationContext(
                    List.of(
                            SystemMessage.from("custom researcher system"),
                            UserMessage.from("custom researcher input")
                    ),
                    context.getToolSpecifications(),
                    null
            );
        }
    }
}
