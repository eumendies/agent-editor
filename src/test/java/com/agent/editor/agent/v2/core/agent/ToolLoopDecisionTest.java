package com.agent.editor.agent.v2.core.agent;

import com.agent.editor.agent.v2.reflexion.ReflexionCritique;
import com.agent.editor.agent.v2.reflexion.ReflexionVerdict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ToolLoopDecisionTest {

    @Test
    void shouldExposeToolCallDecisionData() {
        ToolCall call = new ToolCall("editDocument", "{\"content\":\"hi\"}");
        ToolLoopDecision.ToolCalls decision = new ToolLoopDecision.ToolCalls(List.of(call), "need to edit");

        assertEquals(1, decision.getCalls().size());
        assertNotNull(decision.getCalls().get(0).getId());
        assertEquals("editDocument", decision.getCalls().get(0).getName());
        assertEquals("need to edit", decision.getReasoning());
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

        assertEquals(ReflexionVerdict.REVISE, critique.getVerdict());
        assertEquals("Tighten the conclusion", critique.getFeedback());
        assertEquals("The ending repeats earlier points", critique.getReasoning());
    }
}
