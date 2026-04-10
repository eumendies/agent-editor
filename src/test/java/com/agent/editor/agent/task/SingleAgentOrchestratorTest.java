package com.agent.editor.agent.task;

import com.agent.editor.agent.core.agent.Agent;
import com.agent.editor.agent.core.agent.AgentType;
import com.agent.editor.agent.core.agent.ToolLoopAgent;
import com.agent.editor.agent.core.agent.ToolLoopDecision;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.runtime.ExecutionRequest;
import com.agent.editor.agent.core.runtime.ExecutionResult;
import com.agent.editor.agent.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.core.runtime.ToolLoopExecutionRuntime;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.core.state.DocumentSnapshot;
import com.agent.editor.agent.react.ReActAgentOrchestrator;
import com.agent.editor.agent.react.ReactAgentContextFactory;
import com.agent.editor.agent.core.state.TaskStatus;
import com.agent.editor.agent.support.NoOpMemoryCompressors;
import com.agent.editor.agent.tool.ExecutionToolAccessPolicy;
import com.agent.editor.agent.tool.document.DocumentToolAccessPolicy;
import com.agent.editor.agent.tool.document.DocumentToolMode;
import com.agent.editor.agent.tool.document.DocumentToolNames;
import com.agent.editor.agent.tool.memory.MemoryToolAccessPolicy;
import com.agent.editor.agent.tool.memory.MemoryToolNames;
import com.agent.editor.config.DocumentToolModeProperties;
import com.agent.editor.service.StructuredDocumentService;
import com.agent.editor.agent.tool.ToolRegistry;
import com.agent.editor.utils.rag.markdown.MarkdownSectionTreeBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleAgentOrchestratorTest {

    @Test
    void shouldReturnTaskResultForSingleAgentExecution() {
        ExecutionRuntime runtime = new ToolLoopExecutionRuntime(
                new ToolRegistry(),
                event -> {}
        );
        Agent agent = new StubAgent();
        ReActAgentOrchestrator orchestrator = new ReActAgentOrchestrator(
                runtime,
                agent,
                com.agent.editor.testsupport.AgentTestFixtures.reactAgentContextFactory(NoOpMemoryCompressors.noop()),
                executionToolAccessPolicy(100)
        );

        TaskRequest request = new TaskRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "finish",
                3,
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("previous turn")
                ))
        );

        TaskResult result = orchestrator.execute(request);

        assertEquals(TaskStatus.COMPLETED, result.getStatus());
        assertEquals("body", result.getFinalContent());
        ChatTranscriptMemory memory = (ChatTranscriptMemory) result.getMemory();
        assertTrue(memory.getMessages().stream().anyMatch(message -> "previous turn".equals(message.getText())));
        assertTrue(memory.getMessages().stream().anyMatch(message -> "finish".equals(message.getText())));
        assertTrue(memory.getMessages().stream().anyMatch(message -> "done".equals(message.getText())));
    }

    @Test
    void shouldUseIncrementalDocumentToolsForLongDocument() {
        RecordingExecutionRuntime runtime = new RecordingExecutionRuntime();
        ReActAgentOrchestrator orchestrator = new ReActAgentOrchestrator(
                runtime,
                new StubAgent(),
                com.agent.editor.testsupport.AgentTestFixtures.reactAgentContextFactory(NoOpMemoryCompressors.noop()),
                executionToolAccessPolicy(10)
        );

        orchestrator.execute(new TaskRequest(
                "task-2",
                "session-2",
                AgentType.REACT,
                new DocumentSnapshot("doc-2", "title", "x".repeat(80)),
                "finish",
                3
        ));

        assertEquals(List.of(
                DocumentToolNames.READ_DOCUMENT_NODE,
                DocumentToolNames.PATCH_DOCUMENT_NODE,
                DocumentToolNames.SEARCH_CONTENT,
                MemoryToolNames.SEARCH_MEMORY,
                MemoryToolNames.UPSERT_MEMORY
        ), runtime.requests().get(0).getAllowedTools());
        assertEquals(DocumentToolMode.INCREMENTAL, runtime.requests().get(0).getDocumentToolMode());
    }

    private DocumentToolAccessPolicy documentToolAccessPolicy(int threshold) {
        return new DocumentToolAccessPolicy(
                new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 4_000, 1_200),
                com.agent.editor.testsupport.ConfigurationTestFixtures.documentToolModeProperties(threshold)
        );
    }

    private ExecutionToolAccessPolicy executionToolAccessPolicy(int threshold) {
        return new ExecutionToolAccessPolicy(
                documentToolAccessPolicy(threshold),
                new MemoryToolAccessPolicy()
        );
    }

    private static final class StubAgent implements ToolLoopAgent {

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public ToolLoopDecision decide(AgentRunContext context) {
            return new ToolLoopDecision.Complete("done", "complete immediately");
        }
    }

    private static final class RecordingExecutionRuntime implements ExecutionRuntime {

        private final List<ExecutionRequest> requests = new ArrayList<>();

        @Override
        public ExecutionResult run(Agent agent, ExecutionRequest request) {
            requests.add(request);
            return new ExecutionResult("done", request.getDocument().getContent());
        }

        private List<ExecutionRequest> requests() {
            return requests;
        }
    }
}
