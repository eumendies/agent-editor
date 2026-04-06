package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.ToolLoopDecision;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.support.NoOpMemoryCompressors;
import com.agent.editor.agent.v2.tool.memory.MemoryToolNames;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MemoryAgentTest {

    @Test
    void shouldReportReactType() {
        MemoryAgent definition = MemoryAgent.blocking(mock(ChatModel.class), new MemoryAgentContextFactory(NoOpMemoryCompressors.noop()));

        assertEquals(AgentType.REACT, definition.type());
    }

    @Test
    void shouldRequireStructuredMemorySummary() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("""
                        {"confirmedConstraints":["keep title hierarchy"],"deprecatedConstraints":[],"activeRisks":[],"guidanceForDownstreamWorkers":"Preserve the current outline."}
                        """))
                .build());
        MemoryAgent definition = MemoryAgent.blocking(chatModel, new MemoryAgentContextFactory(NoOpMemoryCompressors.noop()));

        definition.decide(context(List.of(searchMemoryTool(), upsertMemoryTool())));

        assertEquals(List.of(
                MemoryToolNames.SEARCH_MEMORY,
                MemoryToolNames.UPSERT_MEMORY
        ), toolNames(chatModel.lastRequest));
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, chatModel.lastRequest.messages().get(0));
        assertTrue(systemMessage.text().contains("memory worker"));
        assertTrue(systemMessage.text().contains("DOCUMENT_DECISION"));
        assertTrue(systemMessage.text().contains("USER_PROFILE"));
        assertTrue(systemMessage.text().contains("MemoryWorkerSummary"));
    }

    @Test
    void shouldConvertToolRequestsToToolCallDecision() {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("tool-1")
                .name(MemoryToolNames.SEARCH_MEMORY)
                .arguments("{\"query\":\"preserve document outline\",\"documentId\":\"doc-1\"}")
                .build();
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("need one more memory lookup", List.of(toolRequest)))
                .build());
        MemoryAgent definition = MemoryAgent.blocking(chatModel, new MemoryAgentContextFactory(NoOpMemoryCompressors.noop()));

        ToolLoopDecision toolLoopDecision = definition.decide(context(List.of(searchMemoryTool())));

        ToolLoopDecision.ToolCalls toolCalls = assertInstanceOf(ToolLoopDecision.ToolCalls.class, toolLoopDecision);
        assertEquals(MemoryToolNames.SEARCH_MEMORY, toolCalls.getCalls().get(0).getName());
    }

    @Test
    void shouldParseStructuredMemorySummaryWhenCompleting() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("""
                        {"confirmedConstraints":["keep title hierarchy"],"deprecatedConstraints":["remove speculative claims"],"activeRisks":["old memory may be stale"],"guidanceForDownstreamWorkers":"Preserve the current outline."}
                        """))
                .build());
        MemoryAgent definition = MemoryAgent.blocking(chatModel, new MemoryAgentContextFactory(NoOpMemoryCompressors.noop()));

        ToolLoopDecision decision = definition.decide(context(List.of(searchMemoryTool(), upsertMemoryTool())));

        ToolLoopDecision.Complete<?> complete = assertInstanceOf(ToolLoopDecision.Complete.class, decision);
        MemoryWorkerSummary summary = assertInstanceOf(MemoryWorkerSummary.class, complete.getResult());
        assertEquals(List.of("keep title hierarchy"), summary.getConfirmedConstraints());
        assertEquals(List.of("remove speculative claims"), summary.getDeprecatedConstraints());
        assertEquals(List.of("old memory may be stale"), summary.getActiveRisks());
        assertEquals("Preserve the current outline.", summary.getGuidanceForDownstreamWorkers());
    }

    @Test
    void shouldUseContextFactoryProvidedMessages() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("{}"))
                .build());
        StubMemoryAgentContextFactory contextFactory = new StubMemoryAgentContextFactory();
        MemoryAgent definition = MemoryAgent.blocking(chatModel, contextFactory);

        definition.decide(context(List.of(searchMemoryTool(), upsertMemoryTool())));

        assertEquals(1, contextFactory.buildInvocationCount);
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, chatModel.lastRequest.messages().get(0));
        assertEquals("custom memory system", systemMessage.text());
        UserMessage userMessage = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(1));
        assertEquals("custom memory input", userMessage.singleText());
    }

    private AgentRunContext context(List<ToolSpecification> toolSpecifications) {
        return new AgentRunContext(
                new ExecutionRequest(
                        "task-1",
                        "session-1",
                        AgentType.REACT,
                        new DocumentSnapshot("doc-1", "title", "body"),
                        "retrieve prior document constraints and update durable decisions",
                        3
                ),
                0,
                "body",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("retrieve prior document constraints and update durable decisions")
                )),
                ExecutionStage.RUNNING,
                null,
                toolSpecifications
        );
    }

    private ToolSpecification searchMemoryTool() {
        return ToolSpecification.builder()
                .name(MemoryToolNames.SEARCH_MEMORY)
                .description("search prior document memory")
                .build();
    }

    private ToolSpecification upsertMemoryTool() {
        return ToolSpecification.builder()
                .name(MemoryToolNames.UPSERT_MEMORY)
                .description("upsert document decision memory")
                .build();
    }

    private List<String> toolNames(ChatRequest request) {
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

    private static final class StubMemoryAgentContextFactory extends MemoryAgentContextFactory {

        private int buildInvocationCount;

        private StubMemoryAgentContextFactory() {
            super(NoOpMemoryCompressors.noop());
        }

        @Override
        public com.agent.editor.agent.v2.core.context.ModelInvocationContext buildModelInvocationContext(AgentRunContext context) {
            buildInvocationCount++;
            return new com.agent.editor.agent.v2.core.context.ModelInvocationContext(
                    List.of(
                            SystemMessage.from("custom memory system"),
                            UserMessage.from("custom memory input")
                    ),
                    context.getToolSpecifications(),
                    null
            );
        }
    }
}
