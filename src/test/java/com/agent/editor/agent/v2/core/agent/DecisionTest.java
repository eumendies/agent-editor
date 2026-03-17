package com.agent.editor.agent.v2.core.agent;

import com.agent.editor.agent.v2.reflexion.ReflexionCritique;
import com.agent.editor.agent.v2.reflexion.ReflexionVerdict;
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

    @Test
    void shouldExposeReflexionAgentType() {
        assertEquals(AgentType.REFLEXION, AgentType.valueOf("REFLEXION"));
    }

    @Test
    void shouldRetainReflexionCritiqueFields() {
        ReflexionCritique critique = new ReflexionCritique(
                ReflexionVerdict.REVISE,
                "Tighten the conclusion",
                "The ending repeats earlier points"
        );

        assertEquals(ReflexionVerdict.REVISE, critique.verdict());
        assertEquals("Tighten the conclusion", critique.feedback());
        assertEquals("The ending repeats earlier points", critique.reasoning());
    }
}
