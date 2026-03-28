package com.agent.editor.agent.v2.tool;

import com.agent.editor.agent.v2.tool.document.AppendToDocumentTool;
import com.agent.editor.agent.v2.tool.document.GetDocumentSnapshotTool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistryTest {

    @Test
    void shouldResolveRegisteredToolByName() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new StubToolHandler());

        assertNotNull(registry.get("stubTool"));
        assertEquals(1, registry.specifications().size());
    }

    @Test
    void shouldExposeAppendAndSnapshotDocumentToolsWhenRegistered() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new AppendToDocumentTool());
        registry.register(new GetDocumentSnapshotTool());

        assertNotNull(registry.get("appendToDocument"));
        assertNotNull(registry.get("getDocumentSnapshot"));
        assertTrue(registry.specifications().stream().anyMatch(spec -> "appendToDocument".equals(spec.name())));
        assertTrue(registry.specifications().stream().anyMatch(spec -> "getDocumentSnapshot".equals(spec.name())));
    }

    private static final class StubToolHandler implements ToolHandler {

        @Override
        public String name() {
            return "stubTool";
        }

        @Override
        public ToolResult execute(ToolInvocation invocation, ToolContext context) {
            return new ToolResult("ok");
        }

        @Override
        public dev.langchain4j.agent.tool.ToolSpecification specification() {
            return dev.langchain4j.agent.tool.ToolSpecification.builder()
                    .name("stubTool")
                    .description("stub")
                    .parameters(dev.langchain4j.model.chat.request.json.JsonObjectSchema.builder().build())
                    .build();
        }
    }
}
