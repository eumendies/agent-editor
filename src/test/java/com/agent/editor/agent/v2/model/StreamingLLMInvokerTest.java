package com.agent.editor.agent.v2.model;

import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingLLMInvokerTest {

    @Test
    void shouldPublishTextDeltasAndMergeCompletedToolCallsIntoFinalResult() {
        List<ExecutionEvent> events = new ArrayList<>();
        StreamingLLMInvoker invoker = new StreamingLLMInvoker(
                new FakeStreamingChatModel(handler -> {
                    handler.onPartialResponse("hello ");
                    handler.onCompleteToolCall(new CompleteToolCall(
                            0,
                            ToolExecutionRequest.builder()
                                    .id("tool-1")
                                    .name("searchContent")
                                    .arguments("{\"query\":\"heading\"}")
                                    .build()
                    ));
                    handler.onPartialResponse("world");
                    handler.onCompleteResponse(ChatResponse.builder()
                            .aiMessage(AiMessage.from("hello world"))
                            .build());
                }),
                events::add
        );

        StreamingInvocationResult result = invoker.invoke(
                "task-1",
                ChatRequest.builder()
                        .messages(List.of(UserMessage.from("hello")))
                        .build()
        );

        assertEquals(List.of(
                EventType.TEXT_STREAM_STARTED,
                EventType.TEXT_STREAM_DELTA,
                EventType.TEXT_STREAM_DELTA,
                EventType.TEXT_STREAM_COMPLETED
        ), events.stream().map(ExecutionEvent::getType).toList());
        assertEquals(List.of("", "hello ", "world", ""), events.stream().map(ExecutionEvent::getMessage).toList());
        assertEquals("hello world", result.getText());
        assertEquals(1, result.getToolExecutionRequests().size());
        assertTrue(result.getChatResponse().aiMessage().hasToolExecutionRequests());
        assertEquals("tool-1", result.getToolExecutionRequests().get(0).id());
    }

    @Test
    void shouldEmitSingleDeltaFromCompletedResponseWhenProviderDoesNotPushPartialText() {
        List<ExecutionEvent> events = new ArrayList<>();
        StreamingLLMInvoker invoker = new StreamingLLMInvoker(
                new FakeStreamingChatModel(handler -> handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.from("final text"))
                        .build())),
                events::add
        );

        StreamingInvocationResult result = invoker.invoke(
                "task-2",
                ChatRequest.builder()
                        .messages(List.of(UserMessage.from("hello")))
                        .build()
        );

        assertEquals(List.of(
                EventType.TEXT_STREAM_STARTED,
                EventType.TEXT_STREAM_DELTA,
                EventType.TEXT_STREAM_COMPLETED
        ), events.stream().map(ExecutionEvent::getType).toList());
        assertEquals(List.of("", "final text", ""), events.stream().map(ExecutionEvent::getMessage).toList());
        assertEquals("final text", result.getText());
        assertTrue(result.getToolExecutionRequests().isEmpty());
    }

    private static final class FakeStreamingChatModel implements StreamingChatModel {

        private final StreamingScenario scenario;

        private FakeStreamingChatModel(StreamingScenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            scenario.execute(handler);
        }
    }

    @FunctionalInterface
    private interface StreamingScenario {

        void execute(StreamingChatResponseHandler handler);
    }
}
