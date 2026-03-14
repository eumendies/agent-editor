package com.agent.editor.agent.v2.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ToolRegistryTest {

    @Test
    void shouldResolveRegisteredToolByName() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new StubToolHandler());

        assertNotNull(registry.get("stubTool"));
        assertEquals(1, registry.specifications().size());
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
