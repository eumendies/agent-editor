package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.ToolLoopDecision;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.runtime.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
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

class GroundedWriterAgentTest {

    @Test
    void shouldReportReactType() {
        GroundedWriterAgent definition = new GroundedWriterAgent(null);

        assertEquals(AgentType.REACT, definition.type());
    }

    @Test
    void shouldExposeEditingToolsAndGroundingInstructions() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("Document updated"))
                .build());
        GroundedWriterAgent definition = new GroundedWriterAgent(chatModel);

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
        GroundedWriterAgent definition = new GroundedWriterAgent(chatModel);

        ToolLoopDecision toolLoopDecision = definition.decide(context(List.of(editDocumentTool())));

        ToolLoopDecision.ToolCalls toolCalls = assertInstanceOf(ToolLoopDecision.ToolCalls.class, toolLoopDecision);
        assertEquals("editDocument", toolCalls.getCalls().get(0).getName());
    }

    private AgentRunContext context(List<ToolSpecification> toolSpecifications) {
        return new AgentRunContext(
                new ExecutionRequest(
                        "task-1",
                        "session-1",
                        AgentType.REACT,
                        new DocumentSnapshot("doc-1", "title", "body"),
                        "rewrite the answer using available evidence",
                        3
                ),
                0,
                "body",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("rewrite the answer using available evidence")
                )),
                ExecutionStage.RUNNING,
                null,
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
