package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.*;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.*;
import com.agent.editor.agent.v2.trace.InMemoryTraceStore;
import com.agent.editor.agent.v2.trace.TraceStore;
import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolHandler;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolRegistry;
import com.agent.editor.agent.v2.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolLoopExecutionRuntimeTest {

    @Test
    void shouldCompleteWhenAgentReturnsCompleteDecision() {
        Agent agent = new CompletingAgent();
        ExecutionRuntime runtime = new ToolLoopExecutionRuntime(
                new ToolRegistry(),
                event -> {}
        );
        ExecutionRequest request = new ExecutionRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "finish",
                3
        );

        ExecutionResult result = runtime.run(agent, request);

        assertEquals("done", result.getFinalMessage());
        assertEquals("body", result.getFinalContent());
        assertEquals(ExecutionStage.COMPLETED, result.getFinalState().getStage());
        assertEquals("body", result.getFinalState().getCurrentContent());
        ChatTranscriptMemory transcriptMemory = (ChatTranscriptMemory) result.getFinalState().getMemory();
        assertEquals(1, transcriptMemory.getMessages().size());
        ChatMessage.AiChatMessage aiMessage =
                (ChatMessage.AiChatMessage) transcriptMemory.getMessages().get(0);
        assertEquals("done", aiMessage.getText());
    }

    @Test
    void shouldExecuteToolCallsBeforeCompleting() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new AppendToolHandler());
        ExecutionRuntime runtime = new ToolLoopExecutionRuntime(
                registry,
                event -> {}
        );
        Agent agent = new ToolUsingAgent();
        ExecutionRequest request = new ExecutionRequest(
                "task-2",
                "session-2",
                AgentType.REACT,
                new DocumentSnapshot("doc-2", "title", "body"),
                "use tool",
                3
        );

        ExecutionResult result = runtime.run(agent, request);

        assertEquals("updated", result.getFinalMessage());
        assertEquals("body world", result.getFinalContent());
    }

    @Test
    void shouldRejectToolCallsOutsideAllowedWorkerTools() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new AppendToolHandler());
        ExecutionRuntime runtime = new ToolLoopExecutionRuntime(
                registry,
                event -> {}
        );
        ExecutionRequest request = new ExecutionRequest(
                "task-3",
                "session-3",
                AgentType.REACT,
                new DocumentSnapshot("doc-3", "title", "body"),
                "use tool",
                3,
                List.of("searchContent")
        );

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                runtime.run(new ToolUsingAgent(), request)
        );

        assertEquals("No tool handler registered for appendText", error.getMessage());
    }

    @Test
    void shouldAccumulateToolResultsAcrossIterations() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new AppendToolHandler());
        ExecutionRuntime runtime = new ToolLoopExecutionRuntime(
                registry,
                event -> {}
        );
        ExecutionRequest request = new ExecutionRequest(
                "task-4",
                "session-4",
                AgentType.REACT,
                new DocumentSnapshot("doc-4", "title", "body"),
                "use tool twice",
                4
        );

        ExecutionResult result = runtime.run(new MultiStepToolAgent(), request);

        assertEquals("used two tools", result.getFinalMessage());
        assertEquals("body world world", result.getFinalContent());
    }

    @Test
    void shouldExposeLatestDocumentSnapshotAfterAppendToolInSameLoop() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new AppendToDocumentHandler());
        registry.register(new GetDocumentSnapshotHandler());
        ExecutionRuntime runtime = new ToolLoopExecutionRuntime(
                registry,
                event -> {}
        );
        ExecutionRequest request = new ExecutionRequest(
                "task-4b",
                "session-4b",
                AgentType.REACT,
                new DocumentSnapshot("doc-4b", "title", "body"),
                "append then snapshot",
                4
        );

        ExecutionResult result = runtime.run(new AppendThenSnapshotAgent(), request);

        assertEquals("snapshot read", result.getFinalMessage());
        assertEquals("body world", result.getFinalContent());
        ChatTranscriptMemory transcriptMemory = (ChatTranscriptMemory) result.getFinalState().getMemory();
        assertTrue(transcriptMemory.getMessages().stream().anyMatch(message ->
                message instanceof ChatMessage.ToolExecutionResultChatMessage toolMessage
                        && "getDocumentSnapshot".equals(toolMessage.getName())
                        && "body world".equals(toolMessage.getText())
        ));
    }

    @Test
    void shouldNotCaptureRuntimeTraceForStateAndToolExecution() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new AppendToolHandler());
        TraceStore traceStore = new InMemoryTraceStore();
        ExecutionRuntime runtime = new ToolLoopExecutionRuntime(
                registry,
                event -> {}
        );
        ExecutionRequest request = new ExecutionRequest(
                "task-5",
                "session-5",
                AgentType.REACT,
                new DocumentSnapshot("doc-5", "title", "body"),
                "use tool",
                3
        );

        runtime.run(new ToolUsingAgent(), request);

        assertTrue(traceStore.getByTaskId("task-5").isEmpty());
    }

    @Test
    void shouldResumeFromProvidedExecutionState() {
        ToolRegistry registry = new ToolRegistry();
        ExecutionRuntime runtime = new ToolLoopExecutionRuntime(
                registry,
                event -> {}
        );
        RecordingStateAgent agent = new RecordingStateAgent();
        ExecutionRequest request = new ExecutionRequest(
                "task-6",
                "session-6",
                AgentType.REACT,
                new DocumentSnapshot("doc-6", "title", "original body"),
                "finish",
                3
        );
        AgentRunContext initialState = new AgentRunContext(2, "resumed body");

        ExecutionResult result = runtime.run(agent, request, initialState);

        assertEquals(2, agent.seenIteration);
        assertEquals("resumed body", agent.seenContent);
        assertEquals("resumed", result.getFinalMessage());
        assertEquals("resumed body", result.getFinalContent());
        assertEquals(ExecutionStage.COMPLETED, result.getFinalState().getStage());
    }

    @Test
    void shouldPreserveExistingMemoryWhenToolResultsAreAppended() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new AppendToolHandler());
        ExecutionRuntime runtime = new ToolLoopExecutionRuntime(
                registry,
                event -> {}
        );
        ExecutionRequest request = new ExecutionRequest(
                "task-7",
                "session-7",
                AgentType.REACT,
                new DocumentSnapshot("doc-7", "title", "body"),
                "use tool",
                3
        );
        AgentRunContext initialState = new AgentRunContext(
                null,
                0,
                "body",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("plan step 1"),
                        new ChatMessage.AiChatMessage("thinking")
                )),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );

        ExecutionResult result = runtime.run(new ToolUsingAgent(), request, initialState);

        ChatTranscriptMemory transcriptMemory = (ChatTranscriptMemory) result.getFinalState().getMemory();
        assertTrue(transcriptMemory.getMessages().stream().anyMatch(message ->
                message instanceof ChatMessage.UserChatMessage userMessage
                        && userMessage.getText().contains("plan step 1")
        ));
        assertTrue(transcriptMemory.getMessages().stream().anyMatch(message ->
                message instanceof ChatMessage.AiChatMessage aiMessage
                        && aiMessage.getText().contains("thinking")
        ));
        assertTrue(transcriptMemory.getMessages().stream().noneMatch(message ->
                message instanceof ChatMessage.UserChatMessage userMessage
                        && userMessage.getText().contains("use tool")
        ));
        ChatMessage.AiToolCallChatMessage toolCallMessage = transcriptMemory.getMessages().stream()
                .filter(ChatMessage.AiToolCallChatMessage.class::isInstance)
                .map(ChatMessage.AiToolCallChatMessage.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("need tool", toolCallMessage.getText());
        assertEquals(1, toolCallMessage.getToolCalls().size());
        assertEquals("appendText", toolCallMessage.getToolCalls().get(0).getName());
        assertEquals("{\"suffix\":\" world\"}", toolCallMessage.getToolCalls().get(0).getArguments());
        assertTrue(transcriptMemory.getMessages().stream().anyMatch(message ->
                message instanceof ChatMessage.ToolExecutionResultChatMessage toolMessage
                        && toolMessage.getId() != null
                        && "appendText".equals(toolMessage.getName())
                        && "{\"suffix\":\" world\"}".equals(toolMessage.getArgument())
                        && toolMessage.getText().contains("hello world")
        ));
    }

    private static final class CompletingAgent implements ToolLoopAgent {

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public ToolLoopDecision decide(AgentRunContext context) {
            return new ToolLoopDecision.Complete("done", "complete immediately");
        }
    }

    private static final class ToolUsingAgent implements ToolLoopAgent {

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public ToolLoopDecision decide(AgentRunContext context) {
            if (toolResultCount(context) == 0) {
                return new ToolLoopDecision.ToolCalls(List.of(new ToolCall("appendText", "{\"suffix\":\" world\"}")), "need tool");
            }
            return new ToolLoopDecision.Complete("updated", "tool finished");
        }
    }

    private static final class AppendToolHandler implements ToolHandler {

        @Override
        public String name() {
            return "appendText";
        }

        @Override
        public ToolResult execute(ToolInvocation invocation, ToolContext context) {
            return new ToolResult("hello world", context.getCurrentContent() + " world");
        }

        @Override
        public dev.langchain4j.agent.tool.ToolSpecification specification() {
            return dev.langchain4j.agent.tool.ToolSpecification.builder()
                    .name("appendText")
                    .description("Append text to the current document content")
                    .parameters(dev.langchain4j.model.chat.request.json.JsonObjectSchema.builder()
                            .addStringProperty("suffix")
                            .required("suffix")
                            .build())
                    .build();
        }
    }

    private static final class MultiStepToolAgent implements ToolLoopAgent {

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public ToolLoopDecision decide(AgentRunContext context) {
            if (toolResultCount(context) < 2) {
                return new ToolLoopDecision.ToolCalls(List.of(new ToolCall("appendText", "{\"suffix\":\" world\"}")), "need another tool run");
            }
            return new ToolLoopDecision.Complete("used two tools", "tool history available");
        }
    }

    private static final class AppendThenSnapshotAgent implements ToolLoopAgent {

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public ToolLoopDecision decide(AgentRunContext context) {
            long toolResults = toolResultCount(context);
            if (toolResults == 0) {
                return new ToolLoopDecision.ToolCalls(
                        List.of(new ToolCall("appendToDocument", "{\"content\":\" world\"}")),
                        "append first"
                );
            }
            if (toolResults == 1) {
                return new ToolLoopDecision.ToolCalls(
                        List.of(new ToolCall("getDocumentSnapshot", "{}")),
                        "read latest snapshot"
                );
            }
            return new ToolLoopDecision.Complete("snapshot read", "done");
        }
    }

    private static final class RecordingStateAgent implements ToolLoopAgent {

        private int seenIteration;
        private String seenContent;

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public ToolLoopDecision decide(AgentRunContext context) {
            seenIteration = context.state().getIteration();
            seenContent = context.state().getCurrentContent();
            return new ToolLoopDecision.Complete("resumed", "resumed execution");
        }
    }

    private static long toolResultCount(AgentRunContext context) {
        ChatTranscriptMemory memory = (ChatTranscriptMemory) context.getMemory();
        return memory.getMessages().stream()
                .filter(ChatMessage.ToolExecutionResultChatMessage.class::isInstance)
                .count();
    }

    private static final class AppendToDocumentHandler implements ToolHandler {

        @Override
        public String name() {
            return "appendToDocument";
        }

        @Override
        public ToolResult execute(ToolInvocation invocation, ToolContext context) {
            return new ToolResult("appended", context.getCurrentContent() + " world");
        }

        @Override
        public dev.langchain4j.agent.tool.ToolSpecification specification() {
            return dev.langchain4j.agent.tool.ToolSpecification.builder()
                    .name("appendToDocument")
                    .description("Append text to the end of the document")
                    .parameters(dev.langchain4j.model.chat.request.json.JsonObjectSchema.builder()
                            .addStringProperty("content")
                            .required("content")
                            .build())
                    .build();
        }
    }

    private static final class GetDocumentSnapshotHandler implements ToolHandler {

        @Override
        public String name() {
            return "getDocumentSnapshot";
        }

        @Override
        public ToolResult execute(ToolInvocation invocation, ToolContext context) {
            return new ToolResult(context.getCurrentContent());
        }

        @Override
        public dev.langchain4j.agent.tool.ToolSpecification specification() {
            return dev.langchain4j.agent.tool.ToolSpecification.builder()
                    .name("getDocumentSnapshot")
                    .description("Get the latest current document snapshot")
                    .parameters(dev.langchain4j.model.chat.request.json.JsonObjectSchema.builder().build())
                    .build();
        }
    }
}
