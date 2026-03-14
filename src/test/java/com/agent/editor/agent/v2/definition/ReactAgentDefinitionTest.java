package com.agent.editor.agent.v2.definition;

import com.agent.editor.agent.v2.runtime.ExecutionContext;
import com.agent.editor.agent.v2.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.state.DocumentSnapshot;
import com.agent.editor.agent.v2.state.ExecutionState;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactAgentDefinitionTest {

    @Test
    void shouldReportReactType() {
        ReactAgentDefinition definition = new ReactAgentDefinition(null);

        assertEquals(AgentType.REACT, definition.type());
    }

    @Test
    void shouldConvertPlainModelResponseToCompleteDecision() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("final answer"))
                .build());
        ReactAgentDefinition definition = new ReactAgentDefinition(chatModel);

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
        ReactAgentDefinition definition = new ReactAgentDefinition(chatModel);

        Decision decision = definition.decide(context());

        Decision.ToolCalls toolCalls = assertInstanceOf(Decision.ToolCalls.class, decision);
        assertEquals(1, toolCalls.calls().size());
        assertEquals("editDocument", toolCalls.calls().get(0).name());
        assertEquals("{\"content\":\"new body\"}", toolCalls.calls().get(0).arguments());
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
                new ExecutionState(0, false, "body"),
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
