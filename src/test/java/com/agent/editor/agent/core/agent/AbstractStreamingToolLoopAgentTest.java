package com.agent.editor.agent.core.agent;

import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.context.ModelInvocationContext;
import com.agent.editor.agent.core.runtime.ExecutionRequest;
import com.agent.editor.agent.core.state.DocumentSnapshot;
import com.agent.editor.agent.model.StreamingInvocationResult;
import com.agent.editor.agent.model.StreamingLLMInvoker;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbstractStreamingToolLoopAgentTest {

    @Test
    void shouldRejectConstructionWhenNoModelInvokerIsProvided() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new StubStreamingToolLoopAgent(null, null)
        );

        assertEquals("Either chatModel or streamingLLMInvoker must be provided", exception.getMessage());
    }

    @Test
    void shouldInvokeStreamingInvokerWithTaskIdFromContext() {
        RecordingStreamingLLMInvoker streamingLLMInvoker = new RecordingStreamingLLMInvoker();
        StubStreamingToolLoopAgent agent = new StubStreamingToolLoopAgent(null, streamingLLMInvoker);
        AgentRunContext context = new AgentRunContext().withRequest(new ExecutionRequest(
                "task-streaming",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "rewrite",
                3
        ));

        ChatResponse response = agent.callInvokeModel(context, new ModelInvocationContext(
                List.of(UserMessage.from("rewrite")),
                List.of(),
                null
        ));

        assertNotNull(response);
        assertEquals("task-streaming", streamingLLMInvoker.lastTaskId);
        UserMessage userMessage = (UserMessage) streamingLLMInvoker.lastRequest.messages().get(0);
        assertEquals("rewrite", userMessage.singleText());
    }

    @Test
    void shouldBuildBlockingChatRequestWithOptionalResponseFormat() {
        RecordingChatModel chatModel = new RecordingChatModel();
        StubStreamingToolLoopAgent agent = new StubStreamingToolLoopAgent(chatModel, null);
        AgentRunContext context = new AgentRunContext(0, "body");
        ResponseFormat responseFormat = ResponseFormat.builder().type(ResponseFormatType.JSON).build();

        ChatResponse response = agent.callInvokeModel(context, new ModelInvocationContext(
                List.of(UserMessage.from("rewrite")),
                List.of(),
                responseFormat
        ));

        assertNotNull(response);
        assertSame(chatModel.response, response);
        assertNotNull(chatModel.lastRequest);
        assertEquals(ResponseFormatType.JSON, chatModel.lastRequest.responseFormat().type());
    }

    private static final class StubStreamingToolLoopAgent extends AbstractStreamingToolLoopAgent {

        private StubStreamingToolLoopAgent(ChatModel chatModel, StreamingLLMInvoker streamingLLMInvoker) {
            super(chatModel, streamingLLMInvoker);
        }

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public ToolLoopDecision decide(AgentRunContext context) {
            throw new UnsupportedOperationException("not needed");
        }

        private ChatResponse callInvokeModel(AgentRunContext context, ModelInvocationContext invocationContext) {
            return invokeModel(context, invocationContext);
        }
    }

    private static final class RecordingChatModel implements ChatModel {

        private final ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .build();
        private ChatRequest lastRequest;

        @Override
        public ChatResponse chat(ChatRequest request) {
            this.lastRequest = request;
            return response;
        }
    }

    private static final class RecordingStreamingLLMInvoker extends StreamingLLMInvoker {

        private String lastTaskId;
        private ChatRequest lastRequest;

        private RecordingStreamingLLMInvoker() {
            super(null, event -> {});
        }

        @Override
        public StreamingInvocationResult invoke(String taskId, ChatRequest request) {
            this.lastTaskId = taskId;
            this.lastRequest = request;
            return new StreamingInvocationResult(
                    ChatResponse.builder().aiMessage(AiMessage.from("done")).build(),
                    "done",
                    List.of()
            );
        }
    }
}
