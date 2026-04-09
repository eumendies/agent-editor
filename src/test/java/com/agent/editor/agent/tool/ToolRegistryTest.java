package com.agent.editor.agent.tool;

import com.agent.editor.agent.tool.document.AppendToDocumentTool;
import com.agent.editor.agent.tool.document.GetDocumentSnapshotTool;
import com.agent.editor.agent.tool.document.PatchDocumentNodeTool;
import com.agent.editor.agent.tool.document.ReadDocumentNodeTool;
import com.agent.editor.service.StructuredDocumentService;
import com.agent.editor.utils.rag.markdown.MarkdownSectionTreeBuilder;
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

    @Test
    void shouldExposeStructuredDocumentToolsWhenRegistered() {
        ToolRegistry registry = new ToolRegistry();
        StructuredDocumentService structuredDocumentService =
                new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 120, 60);
        registry.register(new ReadDocumentNodeTool(structuredDocumentService));
        registry.register(new PatchDocumentNodeTool(structuredDocumentService));

        assertNotNull(registry.get("readDocumentNode"));
        assertNotNull(registry.get("patchDocumentNode"));
        assertTrue(registry.specifications().stream().anyMatch(spec -> "readDocumentNode".equals(spec.name())));
        assertTrue(registry.specifications().stream().anyMatch(spec -> "patchDocumentNode".equals(spec.name())));
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
