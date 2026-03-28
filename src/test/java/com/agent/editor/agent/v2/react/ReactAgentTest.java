package com.agent.editor.agent.v2.react;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.runtime.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactAgentTest {

    @Test
    void shouldReportReactType() {
        ReactAgent definition = new ReactAgent(null);

        assertEquals(AgentType.REACT, definition.type());
    }

    @Test
    void shouldConvertPlainModelResponseToCompleteDecision() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("final answer"))
                .build());
        ReactAgent definition = new ReactAgent(chatModel);

        Decision decision = definition.decide(context());

        Decision.Complete complete = assertInstanceOf(Decision.Complete.class, decision);
        assertEquals("final answer", complete.getResult());
        assertNotNull(chatModel.lastRequest);
        assertEquals(2, chatModel.lastRequest.messages().size());
        assertInstanceOf(SystemMessage.class, chatModel.lastRequest.messages().get(0));
        UserMessage userMessage = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(1));
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
        ReactAgent definition = new ReactAgent(chatModel);

        Decision decision = definition.decide(context());

        Decision.ToolCalls toolCalls = assertInstanceOf(Decision.ToolCalls.class, decision);
        assertEquals(1, toolCalls.getCalls().size());
        assertEquals("editDocument", toolCalls.getCalls().get(0).getName());
        assertEquals("{\"content\":\"new body\"}", toolCalls.getCalls().get(0).getArguments());
    }

    @Test
    void shouldInstructWritingRequestsToUseEditDocumentByDefault() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("updated"))
                .build());
        ReactAgent definition = new ReactAgent(chatModel);

        definition.decide(context());

        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, chatModel.lastRequest.messages().get(0));
        String prompt = systemMessage.text();
        assertTrue(prompt.contains("must call editDocument"));
        assertTrue(prompt.contains("overwrite the entire document"));
        assertTrue(prompt.contains("explain, analyze, answer questions, or discuss options"));
        assertTrue(prompt.contains("Think step by step"));
        assertTrue(prompt.contains("Take ONE action at a time"));
        assertTrue(prompt.contains("Observe the result"));
    }

    @Test
    void shouldAppendTranscriptMemoryMessagesToModelRequestWithoutDuplicatingCurrentTurnPrompt() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("final answer"))
                .build());
        ReactAgent definition = new ReactAgent(chatModel);

        Decision decision = definition.decide(new AgentRunContext(
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

        Decision.Complete complete = assertInstanceOf(Decision.Complete.class, decision);
        assertEquals("final answer", complete.getResult());
        assertEquals(5, chatModel.lastRequest.messages().size());
        UserMessage transcriptStep = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(1));
        assertTrue(transcriptStep.singleText().contains("inspect headings"));
        ToolExecutionResultMessage transcriptToolResult = assertInstanceOf(
                ToolExecutionResultMessage.class,
                chatModel.lastRequest.messages().get(2)
        );
        assertEquals("tool-call-1", transcriptToolResult.id());
        assertEquals("searchContent", transcriptToolResult.toolName());
        assertTrue(transcriptToolResult.text().contains("Search for 'heading': Found"));
        assertInstanceOf(AiMessage.class, chatModel.lastRequest.messages().get(3));
        UserMessage currentTurn = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(4));
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
        ReactAgent definition = new ReactAgent(chatModel);

        Decision decision = assertDoesNotThrow(() -> definition.decide(context()));

        assertInstanceOf(Decision.ToolCalls.class, decision);
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
