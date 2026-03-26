package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.runtime.ExecutionContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.core.state.ExecutionState;
import com.agent.editor.agent.v2.trace.DefaultTraceCollector;
import com.agent.editor.agent.v2.trace.InMemoryTraceStore;
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

class GroundedWriterAgentDefinitionTest {

    @Test
    void shouldReportReactType() {
        GroundedWriterAgentDefinition definition = new GroundedWriterAgentDefinition(
                null,
                new DefaultTraceCollector(new InMemoryTraceStore())
        );

        assertEquals(AgentType.REACT, definition.type());
    }

    @Test
    void shouldExposeEditingToolsAndGroundingInstructions() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("Document updated"))
                .build());
        GroundedWriterAgentDefinition definition = new GroundedWriterAgentDefinition(
                chatModel,
                new DefaultTraceCollector(new InMemoryTraceStore())
        );

        definition.decide(context(List.of(editDocumentTool(), searchContentTool())));

        assertEquals(List.of("editDocument", "searchContent"), assertThatToolNames(chatModel.lastRequest));
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, chatModel.lastRequest.messages().get(0));
        assertTrue(systemMessage.text().contains("grounded writer worker"));
        assertTrue(systemMessage.text().contains("Do not introduce claims"));
        UserMessage currentTurn = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(1));
        assertEquals("rewrite the answer using available evidence", currentTurn.singleText());
    }

    @Test
    void shouldConvertToolRequestsToToolCallDecision() {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("tool-1")
                .name("editDocument")
                .arguments("{\"content\":\"updated\"}")
                .build();
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("apply grounded draft", List.of(toolRequest)))
                .build());
        GroundedWriterAgentDefinition definition = new GroundedWriterAgentDefinition(
                chatModel,
                new DefaultTraceCollector(new InMemoryTraceStore())
        );

        Decision decision = definition.decide(context(List.of(editDocumentTool())));

        Decision.ToolCalls toolCalls = assertInstanceOf(Decision.ToolCalls.class, decision);
        assertEquals("editDocument", toolCalls.calls().get(0).name());
    }

    private ExecutionContext context(List<ToolSpecification> toolSpecifications) {
        return new ExecutionContext(
                new ExecutionRequest(
                        "task-1",
                        "session-1",
                        AgentType.REACT,
                        new DocumentSnapshot("doc-1", "title", "body"),
                        "rewrite the answer using available evidence",
                        3
                ),
                new ExecutionState(
                        0,
                        "body",
                        new ChatTranscriptMemory(List.of(
                                new ChatMessage.UserChatMessage("rewrite the answer using available evidence")
                        )),
                        ExecutionStage.RUNNING,
                        null
                ),
                toolSpecifications
        );
    }

    private ToolSpecification editDocumentTool() {
        return ToolSpecification.builder()
                .name("editDocument")
                .description("edit the document")
                .build();
    }

    private ToolSpecification searchContentTool() {
        return ToolSpecification.builder()
                .name("searchContent")
                .description("search current content")
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
}
