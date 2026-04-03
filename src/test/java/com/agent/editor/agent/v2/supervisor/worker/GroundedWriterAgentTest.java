package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.ToolLoopDecision;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.support.NoOpMemoryCompressors;
import com.agent.editor.agent.v2.tool.document.DocumentToolNames;
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

class GroundedWriterAgentTest {

    @Test
    void shouldReportReactType() {
        GroundedWriterAgent definition = GroundedWriterAgent.blocking(mock(ChatModel.class), new GroundedWriterAgentContextFactory(NoOpMemoryCompressors.noop()));

        assertEquals(AgentType.REACT, definition.type());
    }

    @Test
    void shouldExposeEditingToolsAndGroundingInstructions() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("Document updated"))
                .build());
        GroundedWriterAgent definition = GroundedWriterAgent.blocking(chatModel, new GroundedWriterAgentContextFactory(NoOpMemoryCompressors.noop()));

        definition.decide(context(List.of(
                readDocumentNodeTool(),
                patchDocumentNodeTool(),
                editDocumentTool(),
                appendToDocumentTool(),
                getDocumentSnapshotTool(),
                searchContentTool()
        )));

        assertEquals(List.of(
                DocumentToolNames.READ_DOCUMENT_NODE,
                DocumentToolNames.PATCH_DOCUMENT_NODE,
                DocumentToolNames.EDIT_DOCUMENT,
                DocumentToolNames.APPEND_TO_DOCUMENT,
                DocumentToolNames.GET_DOCUMENT_SNAPSHOT,
                DocumentToolNames.SEARCH_CONTENT
        ), assertThatToolNames(chatModel.lastRequest));
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, chatModel.lastRequest.messages().get(0));
        assertTrue(systemMessage.text().contains("grounded writer worker"));
        assertTrue(systemMessage.text().contains(DocumentToolNames.READ_DOCUMENT_NODE));
        assertTrue(systemMessage.text().contains(DocumentToolNames.PATCH_DOCUMENT_NODE));
        assertTrue(systemMessage.text().contains("Do not introduce claims"));
        assertTrue(systemMessage.text().contains("## Workflow"));
        assertTrue(systemMessage.text().contains("## Evidence Constraints"));
        assertTrue(systemMessage.text().contains("## Tool Rules"));
        assertTrue(!systemMessage.text().contains("Use appendToDocument when you only need to add content to the end of the current document."));
        assertTrue(!systemMessage.text().contains("Use getDocumentSnapshot when you need the latest current document before deciding the next write."));
        UserMessage currentTurn = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(1));
        assertEquals("rewrite the answer using available evidence", currentTurn.singleText());
    }

    @Test
    void shouldConvertToolRequestsToToolCallDecision() {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("tool-1")
                .name(DocumentToolNames.EDIT_DOCUMENT)
                .arguments("{\"content\":\"updated\"}")
                .build();
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("apply grounded draft", List.of(toolRequest)))
                .build());
        GroundedWriterAgent definition = GroundedWriterAgent.blocking(chatModel, new GroundedWriterAgentContextFactory(NoOpMemoryCompressors.noop()));

        ToolLoopDecision toolLoopDecision = definition.decide(context(List.of(editDocumentTool())));

        ToolLoopDecision.ToolCalls toolCalls = assertInstanceOf(ToolLoopDecision.ToolCalls.class, toolLoopDecision);
        assertEquals(DocumentToolNames.EDIT_DOCUMENT, toolCalls.getCalls().get(0).getName());
    }

    @Test
    void shouldUseContextFactoryProvidedMessages() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("Document updated"))
                .build());
        StubGroundedWriterContextFactory contextFactory = new StubGroundedWriterContextFactory();
        GroundedWriterAgent definition = GroundedWriterAgent.blocking(chatModel, contextFactory);

        definition.decide(context(List.of(editDocumentTool())));

        assertEquals(1, contextFactory.buildInvocationCount);
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, chatModel.lastRequest.messages().get(0));
        assertEquals("custom writer system", systemMessage.text());
        UserMessage userMessage = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(1));
        assertEquals("custom writer input", userMessage.singleText());
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
                .name(DocumentToolNames.EDIT_DOCUMENT)
                .description("edit the document")
                .build();
    }

    private ToolSpecification readDocumentNodeTool() {
        return ToolSpecification.builder()
                .name(DocumentToolNames.READ_DOCUMENT_NODE)
                .description("read one document node")
                .build();
    }

    private ToolSpecification patchDocumentNodeTool() {
        return ToolSpecification.builder()
                .name(DocumentToolNames.PATCH_DOCUMENT_NODE)
                .description("patch one document node")
                .build();
    }

    private ToolSpecification searchContentTool() {
        return ToolSpecification.builder()
                .name(DocumentToolNames.SEARCH_CONTENT)
                .description("search current content")
                .build();
    }

    private ToolSpecification appendToDocumentTool() {
        return ToolSpecification.builder()
                .name(DocumentToolNames.APPEND_TO_DOCUMENT)
                .description("append to current content")
                .build();
    }

    private ToolSpecification getDocumentSnapshotTool() {
        return ToolSpecification.builder()
                .name(DocumentToolNames.GET_DOCUMENT_SNAPSHOT)
                .description("get latest document snapshot")
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

    private static final class StubGroundedWriterContextFactory extends GroundedWriterAgentContextFactory {

        private int buildInvocationCount;

        private StubGroundedWriterContextFactory() {
            super(NoOpMemoryCompressors.noop());
        }

        @Override
        public com.agent.editor.agent.v2.core.context.ModelInvocationContext buildModelInvocationContext(AgentRunContext context) {
            buildInvocationCount++;
            return new com.agent.editor.agent.v2.core.context.ModelInvocationContext(
                    List.of(
                            SystemMessage.from("custom writer system"),
                            UserMessage.from("custom writer input")
                    ),
                    context.getToolSpecifications(),
                    null
            );
        }
    }
}
