package com.agent.editor.agent.react;

import com.agent.editor.agent.core.agent.AgentType;
import com.agent.editor.agent.core.agent.ToolLoopDecision;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.runtime.ExecutionRequest;
import com.agent.editor.agent.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.core.state.DocumentSnapshot;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.core.state.ExecutionStage;
import com.agent.editor.agent.support.NoOpMemoryCompressors;
import com.agent.editor.agent.tool.document.DocumentToolMode;
import com.agent.editor.agent.tool.document.DocumentToolNames;
import com.agent.editor.agent.tool.memory.MemoryToolNames;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ReactAgentTest {

    @Test
    void shouldReportReactType() {
        ReactAgent definition = definition(null);

        assertEquals(AgentType.REACT, definition.type());
    }

    @Test
    void shouldConvertPlainModelResponseToCompleteDecision() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("final answer"))
                .tokenUsage(new TokenUsage(10, 5, 15))
                .build());
        ReactAgent definition = definition(chatModel);
        AgentRunContext context = context();

        ToolLoopDecision toolLoopDecision = definition.decide(context);

        ToolLoopDecision.Complete complete = assertInstanceOf(ToolLoopDecision.Complete.class, toolLoopDecision);
        assertEquals("final answer", complete.getResult());
        ChatTranscriptMemory transcriptMemory = assertInstanceOf(ChatTranscriptMemory.class, context.getMemory());
        assertEquals(15, transcriptMemory.getLastObservedTotalTokens());
        assertNotNull(chatModel.lastRequest);
        assertEquals(3, chatModel.lastRequest.messages().size());
        assertInstanceOf(SystemMessage.class, chatModel.lastRequest.messages().get(0));
        UserMessage stateMessage = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(1));
        assertTrue(stateMessage.singleText().contains("## Current Document Content"));
        assertTrue(stateMessage.singleText().contains("body"));
        UserMessage userMessage = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(2));
        assertEquals("rewrite this", userMessage.singleText());
        assertTrue(userMessage.singleText().contains("rewrite this"));
    }

    @Test
    void shouldConvertToolRequestsToToolCallDecision() {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("tool-1")
                .name("editDocument")
                .arguments("{\"content\":\"new body\"}")
                .build();
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("need tool", java.util.List.of(toolRequest)))
                .build());
        ReactAgent definition = definition(chatModel);

        ToolLoopDecision toolLoopDecision = definition.decide(context());

        ToolLoopDecision.ToolCalls toolCalls = assertInstanceOf(ToolLoopDecision.ToolCalls.class, toolLoopDecision);
        assertEquals(1, toolCalls.getCalls().size());
        assertEquals("editDocument", toolCalls.getCalls().get(0).getName());
        assertEquals("{\"content\":\"new body\"}", toolCalls.getCalls().get(0).getArguments());
    }

    @Test
    void shouldInstructWritingRequestsToUseEditDocumentByDefault() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("updated"))
                .build());
        ReactAgent definition = definition(chatModel);

        ExecutionRequest incrementalRequest = new ExecutionRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "# Intro\n\nbody"),
                "rewrite this",
                3
        );
        incrementalRequest.setDocumentToolMode(DocumentToolMode.INCREMENTAL);
        definition.decide(context()
                .withRequest(incrementalRequest)
                .withCurrentContent("# Intro\n\nbody")
                .withToolSpecifications(java.util.List.of(
                        ToolSpecification.builder()
                                .name(DocumentToolNames.READ_DOCUMENT_NODE)
                                .description("read document node")
                                .build(),
                        ToolSpecification.builder()
                                .name(DocumentToolNames.PATCH_DOCUMENT_NODE)
                                .description("patch document node")
                                .build()
                )));

        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, chatModel.lastRequest.messages().get(0));
        String prompt = systemMessage.text();
        assertTrue(prompt.contains("## Role"));
        assertTrue(prompt.contains("## Workflow"));
        assertTrue(prompt.contains("## Tool Rules"));
        assertTrue(prompt.contains("## Forbidden Actions"));
        assertTrue(prompt.contains("## Output Rules"));
        assertTrue(prompt.contains("readDocumentNode"));
        assertTrue(prompt.contains("patchDocumentNode"));
        assertTrue(!prompt.contains("## Document Model"));
        assertTrue(!prompt.contains("## Document Structure JSON"));
        assertTrue(!prompt.contains("\"nodeId\":\"node-1\""));
        assertTrue(!prompt.contains("must call editDocument"));
        assertTrue(!prompt.contains("appendToDocument when you only need to append"));
        assertTrue(!prompt.contains("getDocumentSnapshot when you need the latest full document content"));
        assertTrue(prompt.contains("explain, analyze, answer questions, or discuss options"));
        assertTrue(prompt.contains("Think step by step"));
        assertTrue(prompt.contains("Take ONE action at a time"));
        assertTrue(prompt.contains("Observe the result"));
        UserMessage stateMessage = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(1));
        assertTrue(stateMessage.singleText().contains("## Document Model"));
        assertTrue(stateMessage.singleText().contains("The document structure is provided as JSON."));
        assertTrue(stateMessage.singleText().contains("must use the nodeId values from the JSON structure"));
        assertTrue(stateMessage.singleText().contains("## Document Structure JSON"));
        assertTrue(stateMessage.singleText().contains("\"nodeId\":\"node-1\""));
    }

    @Test
    void shouldPassProvidedWebSearchToolSpecificationIntoChatRequest() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("final answer"))
                .build());
        ReactAgent definition = definition(chatModel);
        AgentRunContext context = context().withToolSpecifications(java.util.List.of(
                ToolSpecification.builder()
                        .name(DocumentToolNames.WEB_SEARCH)
                        .description("search the public web")
                        .build(),
                ToolSpecification.builder()
                        .name(MemoryToolNames.SEARCH_MEMORY)
                        .description("search memory")
                        .build()
        ));

        definition.decide(context);

        assertEquals(
                java.util.List.of(DocumentToolNames.WEB_SEARCH, MemoryToolNames.SEARCH_MEMORY),
                chatModel.lastRequest.toolSpecifications().stream().map(ToolSpecification::name).toList()
        );
    }

    @Test
    void shouldAppendTranscriptMemoryMessagesToModelRequestWithoutDuplicatingCurrentTurnPrompt() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("final answer"))
                .build());
        ReactAgent definition = definition(chatModel);

        ToolLoopDecision toolLoopDecision = definition.decide(new AgentRunContext(
                new ExecutionRequest(
                        "task-2",
                        "session-1",
                        AgentType.REACT,
                        new DocumentSnapshot("doc-1", "title", "original body"),
                        "rewrite this",
                        3
                ),
                1,
                "revised body",
                new ChatTranscriptMemory(java.util.List.of(
                        new ChatMessage.UserChatMessage("Plan step 1: inspect headings"),
                        new ChatMessage.ToolExecutionResultChatMessage(
                                "tool-call-1",
                                "searchContent",
                                "{\"query\":\"heading\"}",
                                "Search for 'heading': Found"
                        ),
                        new ChatMessage.AiChatMessage("I found the heading block."),
                        new ChatMessage.UserChatMessage("rewrite this")
                )),
                ExecutionStage.RUNNING,
                null,
                java.util.List.of()
        ));

        ToolLoopDecision.Complete complete = assertInstanceOf(ToolLoopDecision.Complete.class, toolLoopDecision);
        assertEquals("final answer", complete.getResult());
        assertEquals(6, chatModel.lastRequest.messages().size());
        UserMessage stateMessage = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(1));
        assertTrue(stateMessage.singleText().contains("## Current Document Content"));
        assertTrue(stateMessage.singleText().contains("revised body"));
        UserMessage transcriptStep = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(2));
        assertTrue(transcriptStep.singleText().contains("inspect headings"));
        ToolExecutionResultMessage transcriptToolResult = assertInstanceOf(
                ToolExecutionResultMessage.class,
                chatModel.lastRequest.messages().get(3)
        );
        assertEquals("tool-call-1", transcriptToolResult.id());
        assertEquals("searchContent", transcriptToolResult.toolName());
        assertTrue(transcriptToolResult.text().contains("Search for 'heading': Found"));
        assertInstanceOf(AiMessage.class, chatModel.lastRequest.messages().get(4));
        UserMessage currentTurn = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(5));
        assertEquals("rewrite this", currentTurn.singleText());
    }

    @Test
    void shouldHandleToolCallResponsesWithoutRawText() {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("tool-2")
                .name("editDocument")
                .arguments("{\"content\":\"new body\"}")
                .build();
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from(null, java.util.List.of(toolRequest)))
                .build());
        ReactAgent definition = definition(chatModel);

        ToolLoopDecision toolLoopDecision = assertDoesNotThrow(() -> definition.decide(context()));

        assertInstanceOf(ToolLoopDecision.ToolCalls.class, toolLoopDecision);
    }

    @Test
    void shouldLeaveObservedTokensUnsetWhenResponseUsageIsMissing() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("final answer"))
                .build());
        ReactAgent definition = definition(chatModel);
        AgentRunContext context = context();

        assertDoesNotThrow(() -> definition.decide(context));

        ChatTranscriptMemory transcriptMemory = assertInstanceOf(ChatTranscriptMemory.class, context.getMemory());
        assertEquals(null, transcriptMemory.getLastObservedTotalTokens());
    }

    private AgentRunContext context() {
        return new AgentRunContext(
                new ExecutionRequest(
                        "task-1",
                        "session-1",
                        AgentType.REACT,
                        new DocumentSnapshot("doc-1", "title", "body"),
                        "rewrite this",
                        3
                ),
                0,
                "body",
                new ChatTranscriptMemory(java.util.List.of(
                        new ChatMessage.UserChatMessage("rewrite this")
                )),
                ExecutionStage.RUNNING,
                null,
                java.util.List.of()
        );
    }

    private ReactAgent definition(ChatModel chatModel) {
        return ReactAgent.blocking(
                chatModel != null ? chatModel : mock(ChatModel.class),
                com.agent.editor.testsupport.AgentTestFixtures.reactAgentContextFactory(NoOpMemoryCompressors.noop())
        );
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
}
