package com.agent.editor.agent.v2.tool.document;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.ToolCall;
import com.agent.editor.agent.v2.core.agent.ToolLoopAgent;
import com.agent.editor.agent.v2.core.agent.ToolLoopDecision;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.runtime.ToolLoopExecutionRuntime;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolHandler;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolRegistry;
import com.agent.editor.agent.v2.tool.ToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructuredDocumentToolContextTest {

    @Test
    void shouldPassDocumentMetadataIntoToolContext() {
        ToolRegistry registry = new ToolRegistry();
        RecordingStructuredTool tool = new RecordingStructuredTool();
        registry.register(tool);
        ToolLoopExecutionRuntime runtime = new ToolLoopExecutionRuntime(registry, event -> {
        });

        ExecutionResult<?> result = runtime.run(new StructuredContextAgent(), new ExecutionRequest(
                "task-ctx",
                "session-ctx",
                AgentType.REACT,
                new DocumentSnapshot("doc-ctx", "Structured Title", "# Intro\n\nbody"),
                "inspect",
                2
        ));

        assertEquals("recorded", result.getFinalMessage());
        assertEquals("task-ctx", tool.seenContext.getTaskId());
        assertEquals("doc-ctx", tool.seenContext.getDocumentId());
        assertEquals("Structured Title", tool.seenContext.getDocumentTitle());
        assertEquals("# Intro\n\nbody", tool.seenContext.getCurrentContent());
    }

    private static final class StructuredContextAgent implements ToolLoopAgent {

        private boolean usedTool;

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public ToolLoopDecision decide(AgentRunContext context) {
            if (!usedTool) {
                usedTool = true;
                return new ToolLoopDecision.ToolCalls(
                        java.util.List.of(new ToolCall("call-1", "recordStructuredContext", "{}")),
                        "inspect context"
                );
            }
            return new ToolLoopDecision.Complete<>("recorded", "recorded");
        }
    }

    private static final class RecordingStructuredTool implements ToolHandler {

        private ToolContext seenContext;

        @Override
        public String name() {
            return "recordStructuredContext";
        }

        @Override
        public dev.langchain4j.agent.tool.ToolSpecification specification() {
            return dev.langchain4j.agent.tool.ToolSpecification.builder()
                    .name(name())
                    .description("record tool context")
                    .parameters(dev.langchain4j.model.chat.request.json.JsonObjectSchema.builder().build())
                    .build();
        }

        @Override
        public ToolResult execute(ToolInvocation invocation, ToolContext context) {
            this.seenContext = context;
            return new ToolResult("recorded");
        }
    }
}
