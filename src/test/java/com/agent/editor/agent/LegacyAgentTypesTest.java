package com.agent.editor.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyAgentTypesTest {

    @Test
    void shouldMarkLegacyAgentTypesAsDeprecated() {
        assertTrue(AgentFactory.class.isAnnotationPresent(Deprecated.class));
        assertTrue(BaseAgent.class.isAnnotationPresent(Deprecated.class));
        assertTrue(ReActAgent.class.isAnnotationPresent(Deprecated.class));
        assertTrue(EditorAgentTools.class.isAnnotationPresent(Deprecated.class));
    }
}
