package com.agent.editor.agent.v2.core.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecisionTest {

    @Test
    void shouldExposeToolCallDecisionData() {
        ToolCall call = new ToolCall("editDocument", "{\"content\":\"hi\"}");
        Decision.ToolCalls decision = new Decision.ToolCalls(List.of(call), "need to edit");

        assertEquals(1, decision.calls().size());
        assertEquals("editDocument", decision.calls().get(0).name());
        assertEquals("need to edit", decision.reasoning());
    }
}
