package com.agent.editor.agent.v2.definition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReactAgentDefinitionTest {

    @Test
    void shouldReportReactType() {
        ReactAgentDefinition definition = new ReactAgentDefinition(null);

        assertEquals(AgentType.REACT, definition.type());
    }
}
