package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearcherAgentContextFactoryTest {

    @Test
    void shouldBuildInvocationContextWithSystemPromptAndTranscriptOnly() {
        ResearcherAgentContextFactory factory = new ResearcherAgentContextFactory();

        var invocationContext = factory.buildModelInvocationContext(context());

        assertEquals(5, invocationContext.getMessages().size());
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, invocationContext.getMessages().get(0));
        assertTrue(systemMessage.text().contains("researcher worker"));
        assertTrue(systemMessage.text().contains("Use retrieveKnowledge"));
        assertTrue(systemMessage.text().contains("rewrite the query"));
        assertTrue(systemMessage.text().contains("multiple retrieveKnowledge tool calls"));
        assertTrue(systemMessage.text().contains("ResearcherSummary"));
        assertTrue(systemMessage.text().contains("\"evidenceSummary\": \"string\""));
        assertTrue(systemMessage.text().contains("\"limitations\": \"string\""));
        assertTrue(systemMessage.text().contains("\"uncoveredPoints\": [\"string\"]"));
        UserMessage firstUserMessage = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(1));
        assertEquals("initial grounding request", firstUserMessage.singleText());
        ToolExecutionResultMessage toolMessage = assertInstanceOf(
                ToolExecutionResultMessage.class,
                invocationContext.getMessages().get(2)
        );
        assertEquals("retrieveKnowledge", toolMessage.toolName());
        UserMessage currentTurn = assertInstanceOf(UserMessage.class, invocationContext.getMessages().get(4));
        assertEquals("ground this answer", currentTurn.singleText());
    }

    private AgentRunContext context() {
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
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("initial grounding request"),
                        new ChatMessage.ToolExecutionResultChatMessage(
                                "tool-1",
                                "retrieveKnowledge",
                                "{\"query\":\"agentic rag\"}",
                                "[{\"chunkText\":\"supports supervisor\"}]"
                        ),
                        new ChatMessage.AiChatMessage("I need one more retrieval pass."),
                        new ChatMessage.UserChatMessage("ground this answer")
                )),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );
    }
}
