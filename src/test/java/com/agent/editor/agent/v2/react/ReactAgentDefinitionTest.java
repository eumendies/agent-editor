package com.agent.editor.agent.v2.react;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.runtime.ExecutionContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.state.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionMessage;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.core.state.ExecutionState;
import com.agent.editor.agent.v2.trace.DefaultTraceCollector;
import com.agent.editor.agent.v2.trace.InMemoryTraceStore;
import com.agent.editor.agent.v2.trace.TraceCategory;
import com.agent.editor.agent.v2.trace.TraceStore;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
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

class ReactAgentDefinitionTest {

    @Test
    void shouldReportReactType() {
        ReactAgentDefinition definition = new ReactAgentDefinition(
                null,
                new DefaultTraceCollector(new InMemoryTraceStore())
        );

        assertEquals(AgentType.REACT, definition.type());
    }

    @Test
    void shouldConvertPlainModelResponseToCompleteDecision() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("final answer"))
                .build());
        ReactAgentDefinition definition = new ReactAgentDefinition(
                chatModel,
                new DefaultTraceCollector(new InMemoryTraceStore())
        );

        Decision decision = definition.decide(context());

        Decision.Complete complete = assertInstanceOf(Decision.Complete.class, decision);
        assertEquals("final answer", complete.result());
        assertNotNull(chatModel.lastRequest);
        assertEquals(2, chatModel.lastRequest.messages().size());
        assertInstanceOf(SystemMessage.class, chatModel.lastRequest.messages().get(0));
        UserMessage userMessage = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(1));
        assertTrue(userMessage.singleText().contains("body"));
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
        ReactAgentDefinition definition = new ReactAgentDefinition(
                chatModel,
                new DefaultTraceCollector(new InMemoryTraceStore())
        );

        Decision decision = definition.decide(context());

        Decision.ToolCalls toolCalls = assertInstanceOf(Decision.ToolCalls.class, decision);
        assertEquals(1, toolCalls.calls().size());
        assertEquals("editDocument", toolCalls.calls().get(0).name());
        assertEquals("{\"content\":\"new body\"}", toolCalls.calls().get(0).arguments());
    }

    @Test
    void shouldAppendTranscriptMemoryMessagesToModelRequest() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("final answer"))
                .build());
        ReactAgentDefinition definition = new ReactAgentDefinition(
                chatModel,
                new DefaultTraceCollector(new InMemoryTraceStore())
        );

        Decision decision = definition.decide(new ExecutionContext(
                new ExecutionRequest(
                        "task-2",
                        "session-1",
                        AgentType.REACT,
                        new DocumentSnapshot("doc-1", "title", "original body"),
                        "rewrite this",
                        3
                ),
                new ExecutionState(
                        1,
                        "revised body",
                        new ChatTranscriptMemory(java.util.List.of(
                                new ExecutionMessage.UserExecutionMessage("Plan step 1: inspect headings"),
                                new ExecutionMessage.ToolExecutionResultExecutionMessage("Search for 'heading': Found"),
                                new ExecutionMessage.AiExecutionMessage("I found the heading block.")
                        )),
                        ExecutionStage.RUNNING,
                        null
                ),
                java.util.List.of()
        ));

        Decision.Complete complete = assertInstanceOf(Decision.Complete.class, decision);
        assertEquals("final answer", complete.result());
        assertEquals(5, chatModel.lastRequest.messages().size());
        UserMessage transcriptStep = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(1));
        assertTrue(transcriptStep.singleText().contains("inspect headings"));
        UserMessage transcriptToolResult = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(2));
        assertTrue(transcriptToolResult.singleText().contains("Search for 'heading': Found"));
        assertInstanceOf(AiMessage.class, chatModel.lastRequest.messages().get(3));
        UserMessage currentTurn = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(4));
        assertTrue(currentTurn.singleText().contains("revised body"));
        assertTrue(currentTurn.singleText().contains("rewrite this"));
    }

    @Test
    void shouldCaptureModelRequestAndResponseTrace() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("final answer"))
                .build());
        TraceStore traceStore = new InMemoryTraceStore();
        ReactAgentDefinition definition = new ReactAgentDefinition(
                chatModel,
                new DefaultTraceCollector(traceStore)
        );

        definition.decide(context());

        var traces = traceStore.getByTaskId("task-1");
        assertTrue(traces.stream().anyMatch(trace ->
                trace.category() == TraceCategory.MODEL_REQUEST
                        && "react.model.request".equals(trace.stage())
                        && trace.payload().containsKey("systemPrompt")
                        && trace.payload().containsKey("userPrompt")
        ));
        assertTrue(traces.stream().anyMatch(trace ->
                trace.category() == TraceCategory.MODEL_RESPONSE
                        && "react.model.response".equals(trace.stage())
                        && "final answer".equals(trace.payload().get("rawText"))
        ));
    }

    @Test
    void shouldHandleToolCallResponsesWithoutRawTextWhenTracing() {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("tool-2")
                .name("editDocument")
                .arguments("{\"content\":\"new body\"}")
                .build();
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from(null, java.util.List.of(toolRequest)))
                .build());
        TraceStore traceStore = new InMemoryTraceStore();
        ReactAgentDefinition definition = new ReactAgentDefinition(
                chatModel,
                new DefaultTraceCollector(traceStore)
        );

        Decision decision = assertDoesNotThrow(() -> definition.decide(context()));

        assertInstanceOf(Decision.ToolCalls.class, decision);
        assertTrue(traceStore.getByTaskId("task-1").stream().anyMatch(trace ->
                trace.category() == TraceCategory.MODEL_RESPONSE
                        && trace.payload().containsKey("rawText")
        ));
    }

    private ExecutionContext context() {
        return new ExecutionContext(
                new ExecutionRequest(
                        "task-1",
                        "session-1",
                        AgentType.REACT,
                        new DocumentSnapshot("doc-1", "title", "body"),
                        "rewrite this",
                        3
                ),
                new ExecutionState(0, "body"),
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
