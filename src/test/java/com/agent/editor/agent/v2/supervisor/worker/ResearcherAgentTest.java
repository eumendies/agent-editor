package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
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
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearcherAgentTest {

    @Test
    void shouldReportReactType() {
        ResearcherAgent definition = new ResearcherAgent(null);

        assertEquals(AgentType.REACT, definition.type());
    }

    @Test
    void shouldExposeRetrieveKnowledgeOnly() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("""
                        {"queries":["agentic rag"],"evidenceSummary":"...", "limitations":"...", "uncoveredPoints":[], "chunks":[]}
                        """))
                .build());
        ResearcherAgent definition = new ResearcherAgent(chatModel);

        definition.decide(context(List.of(retrieveKnowledgeTool()), new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("ground this answer")
        ))));

        assertEquals(List.of("retrieveKnowledge"), assertThatToolNames(chatModel.lastRequest));
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, chatModel.lastRequest.messages().get(0));
        assertTrue(systemMessage.text().contains("Use retrieveKnowledge"));
        assertTrue(systemMessage.text().contains("return strict JSON"));
    }

    @Test
    void shouldConvertToolRequestsToToolCallDecision() {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("tool-1")
                .name("retrieveKnowledge")
                .arguments("{\"query\":\"agentic rag\"}")
                .build();
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("need evidence", List.of(toolRequest)))
                .build());
        ResearcherAgent definition = new ResearcherAgent(chatModel);

        Decision decision = definition.decide(context(List.of(retrieveKnowledgeTool()), new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("ground this answer")
        ))));

        Decision.ToolCalls toolCalls = assertInstanceOf(Decision.ToolCalls.class, decision);
        assertEquals(1, toolCalls.getCalls().size());
        assertEquals("retrieveKnowledge", toolCalls.getCalls().get(0).getName());
    }

    @Test
    void shouldAppendTranscriptMemoryMessagesToModelRequest() {
        RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("""
                        {"queries":["agentic rag"],"evidenceSummary":"...", "limitations":"...", "uncoveredPoints":[], "chunks":[]}
                        """))
                .build());
        ResearcherAgent definition = new ResearcherAgent(chatModel);

        Decision decision = definition.decide(context(List.of(retrieveKnowledgeTool()), new ChatTranscriptMemory(List.of(
                new ChatMessage.UserChatMessage("initial grounding request"),
                new ChatMessage.ToolExecutionResultChatMessage(
                        "tool-1",
                        "retrieveKnowledge",
                        "{\"query\":\"agentic rag\"}",
                        "[{\"chunkText\":\"supports supervisor\"}]"
                ),
                new ChatMessage.AiChatMessage("I need one more retrieval pass."),
                new ChatMessage.UserChatMessage("ground this answer")
        ))));

        assertInstanceOf(Decision.Complete.class, decision);
        assertEquals(5, chatModel.lastRequest.messages().size());
        assertInstanceOf(SystemMessage.class, chatModel.lastRequest.messages().get(0));
        UserMessage firstUserMessage = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(1));
        assertEquals("initial grounding request", firstUserMessage.singleText());
        ToolExecutionResultMessage toolMessage = assertInstanceOf(
                ToolExecutionResultMessage.class,
                chatModel.lastRequest.messages().get(2)
        );
        assertEquals("tool-1", toolMessage.id());
        assertEquals("retrieveKnowledge", toolMessage.toolName());
        assertInstanceOf(AiMessage.class, chatModel.lastRequest.messages().get(3));
        UserMessage currentTurn = assertInstanceOf(UserMessage.class, chatModel.lastRequest.messages().get(4));
        assertEquals("ground this answer", currentTurn.singleText());
    }

    private AgentRunContext context(List<ToolSpecification> toolSpecifications, ChatTranscriptMemory memory) {
        return new AgentRunContext(
                new ExecutionRequest(
                        "task-1",
                        "session-1",
                        AgentType.REACT,
                        new DocumentSnapshot("doc-1", "title", "body"),
                        "ground this answer",
                        3
                ),
                0,
                "body",
                memory,
                ExecutionStage.RUNNING,
                null,
                toolSpecifications
        );
    }

    private ToolSpecification retrieveKnowledgeTool() {
        return ToolSpecification.builder()
                .name("retrieveKnowledge")
                .description("retrieve relevant knowledge")
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
