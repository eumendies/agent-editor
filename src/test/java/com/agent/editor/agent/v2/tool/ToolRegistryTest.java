package com.agent.editor.agent.v2.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ToolRegistryTest {

    @Test
    void shouldResolveRegisteredToolByName() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new StubToolHandler());

        assertNotNull(registry.get("stubTool"));
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
    }
}
