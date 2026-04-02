package com.agent.editor.agent.v2.model;

import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class StreamingLLMInvoker {

    private final StreamingChatModel streamingChatModel;
    private final EventPublisher eventPublisher;

    public StreamingLLMInvoker(StreamingChatModel streamingChatModel,
                               EventPublisher eventPublisher) {
        this.streamingChatModel = streamingChatModel;
        this.eventPublisher = eventPublisher;
    }

    public StreamingInvocationResult invoke(String taskId, ChatRequest request) {
        CompletableFuture<StreamingInvocationResult> future = new CompletableFuture<>();
        List<ToolExecutionRequest> completeToolRequests = new ArrayList<>();
        StringBuilder streamedText = new StringBuilder();
        StreamingState streamingState = new StreamingState();

        streamingChatModel.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                if (partialResponse == null || partialResponse.isEmpty()) {
                    return;
                }
                ensureStreamStarted(taskId, streamingState);
                streamedText.append(partialResponse);
                eventPublisher.publish(new ExecutionEvent(EventType.TEXT_STREAM_DELTA, taskId, partialResponse));
            }

            @Override
            public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                if (completeToolCall == null || completeToolCall.toolExecutionRequest() == null) {
                    return;
                }
                completeToolRequests.add(completeToolCall.toolExecutionRequest());
            }

            @Override
            public void onCompleteResponse(ChatResponse chatResponse) {
                try {
                    ChatResponse normalizedResponse = normalizeCompletedResponse(
                            taskId,
                            chatResponse,
                            streamedText,
                            completeToolRequests,
                            streamingState
                    );
                    future.complete(new StreamingInvocationResult(
                            normalizedResponse,
                            safeText(normalizedResponse.aiMessage()),
                            normalizedResponse.aiMessage().toolExecutionRequests()
                    ));
                } catch (Exception exception) {
                    future.completeExceptionally(exception);
                }
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        return future.join();
    }

    /*
     * 把流式阶段收集到的增量文本和完整 tool call 收敛成最终 ChatResponse。
     * 这里会统一三件事：
     * 1. 优先使用流式累计文本；如果 provider 没有推 partial text，则回退到最终 aiMessage.text()。
     * 2. 在只有最终文本、没有 partial text 的情况下补齐 TEXT_STREAM_STARTED/DELTA/COMPLETED 事件，保证前端事件序列完整。
     * 3. 优先使用 onCompleteToolCall 收集到的完整工具调用，并在必要时重建 AiMessage。
     *
     * 这样下游 agent/runtime 始终只消费“完整、稳定”的最终响应，
     * 不需要感知底层模型是否真的逐 token 流出，或 tool call 是通过哪一种回调凑齐的。
     */
    private ChatResponse normalizeCompletedResponse(String taskId,
                                                    ChatResponse chatResponse,
                                                    StringBuilder streamedText,
                                                    List<ToolExecutionRequest> completeToolRequests,
                                                    StreamingState streamingState) {
        AiMessage aiMessage = chatResponse.aiMessage();
        String finalText = streamedText.length() > 0 ? streamedText.toString() : safeText(aiMessage);
        if (!finalText.isEmpty() && streamedText.isEmpty()) {
            ensureStreamStarted(taskId, streamingState);
            eventPublisher.publish(new ExecutionEvent(EventType.TEXT_STREAM_DELTA, taskId, finalText));
        }
        if (streamingState.started) {
            eventPublisher.publish(new ExecutionEvent(EventType.TEXT_STREAM_COMPLETED, taskId, ""));
        }

        List<ToolExecutionRequest> finalToolRequests = !completeToolRequests.isEmpty()
                ? List.copyOf(completeToolRequests)
                : aiMessage.toolExecutionRequests();
        if (finalText.equals(safeText(aiMessage)) && finalToolRequests.equals(aiMessage.toolExecutionRequests())) {
            return chatResponse;
        }

        // 流式阶段的文本和完整 tool call 要在这里一次性收敛成最终 AiMessage，
        // 下游 runtime 只能看到完整 decision，不能感知 provider 的增量回调细节。
        AiMessage mergedMessage = AiMessage.builder()
                .text(finalText)
                .thinking(aiMessage.thinking())
                .toolExecutionRequests(finalToolRequests)
                .attributes(aiMessage.attributes() == null ? Map.of() : aiMessage.attributes())
                .build();
        return chatResponse.toBuilder()
                .aiMessage(mergedMessage)
                .build();
    }

    private void ensureStreamStarted(String taskId, StreamingState streamingState) {
        if (streamingState.started) {
            return;
        }
        streamingState.started = true;
        eventPublisher.publish(new ExecutionEvent(EventType.TEXT_STREAM_STARTED, taskId, ""));
    }

    private String safeText(AiMessage aiMessage) {
        if (aiMessage == null || aiMessage.text() == null) {
            return "";
        }
        return aiMessage.text();
    }

    private static final class StreamingState {

        private boolean started;
    }
}
