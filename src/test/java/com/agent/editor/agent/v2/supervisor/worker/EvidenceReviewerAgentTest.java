package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.ToolLoopDecision;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
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

class EvidenceReviewerAgentTest {

    @Test
    void shouldReportReactType() {
        EvidenceReviewerAgent definition = new EvidenceReviewerAgent(null);

        assertEquals(AgentType.REACT, definition.type());
    }

    @Test
    void shouldRequireStructuredReviewFeedback() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("""
                        {"verdict":"PASS","instructionSatisfied":true,"evidenceGrounded":true,"unsupportedClaims":[],"missingRequirements":[],"feedback":"ok","reasoning":"complete"}
                        """))
                .build());
        EvidenceReviewerAgent definition = new EvidenceReviewerAgent(chatModel);

        definition.decide(context(List.of(searchContentTool(), analyzeDocumentTool())));

        assertEquals(List.of("searchContent", "analyzeDocument"), assertThatToolNames(chatModel.lastRequest));
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, chatModel.lastRequest.messages().get(0));
        assertTrue(systemMessage.text().contains("reviewer worker"));
        assertTrue(systemMessage.text().contains("ReviewerFeedback"));
    }

    @Test
    void shouldConvertToolRequestsToToolCallDecision() {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("tool-1")
                .name("analyzeDocument")
                .arguments("{\"focus\":\"unsupported claims\"}")
                .build();
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("need one more verification step", List.of(toolRequest)))
                .build());
        EvidenceReviewerAgent definition = new EvidenceReviewerAgent(chatModel);

        ToolLoopDecision toolLoopDecision = definition.decide(context(List.of(analyzeDocumentTool())));

        ToolLoopDecision.ToolCalls toolCalls = assertInstanceOf(ToolLoopDecision.ToolCalls.class, toolLoopDecision);
        assertEquals("analyzeDocument", toolCalls.getCalls().get(0).getName());
    }

    @Test
    void shouldUseContextFactoryProvidedMessages() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("{}"))
                .build());
        StubEvidenceReviewerContextFactory contextFactory = new StubEvidenceReviewerContextFactory();
        EvidenceReviewerAgent definition = new EvidenceReviewerAgent(chatModel, contextFactory);

        definition.decide(context(List.of(searchContentTool(), analyzeDocumentTool())));

        assertEquals(1, contextFactory.buildInvocationCount);
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, chatModel.lastRequest.messages().get(0));
        assertEquals("custom reviewer system", systemMessage.text());
        UserMessage userMessage = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(1));
        assertEquals("custom reviewer input", userMessage.singleText());
    }

    private AgentRunContext context(List<ToolSpecification> toolSpecifications) {
        return new AgentRunContext(
                new ExecutionRequest(
                        "task-1",
                        "session-1",
                        AgentType.REACT,
                        new DocumentSnapshot("doc-1", "title", "body"),
                        "review whether the answer follows the instruction and evidence",
                        3
                ),
                0,
                "body",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("review whether the answer follows the instruction and evidence")
                )),
                ExecutionStage.RUNNING,
                null,
                toolSpecifications
        );
    }

    private ToolSpecification searchContentTool() {
        return ToolSpecification.builder()
                .name("searchContent")
                .description("search current content")
                .build();
    }

    private ToolSpecification analyzeDocumentTool() {
        return ToolSpecification.builder()
                .name("analyzeDocument")
                .description("analyze current document")
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

    private static final class StubEvidenceReviewerContextFactory extends EvidenceReviewerAgentContextFactory {

        private int buildInvocationCount;

        @Override
        public com.agent.editor.agent.v2.core.context.ModelInvocationContext buildModelInvocationContext(AgentRunContext context) {
            buildInvocationCount++;
            return new com.agent.editor.agent.v2.core.context.ModelInvocationContext(
                    List.of(
                            SystemMessage.from("custom reviewer system"),
                            UserMessage.from("custom reviewer input")
                    ),
                    context.getToolSpecifications(),
                    null
            );
        }
    }
}
