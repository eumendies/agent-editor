package com.agent.editor.agent.v2.util;

import com.agent.editor.agent.v2.reflexion.ReflexionCritique;
import com.agent.editor.agent.v2.reflexion.ReflexionVerdict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StructuredOutputParsersTest {

    @Test
    void shouldParseDirectJson() {
        ReflexionCritique critique = StructuredOutputParsers.parseJsonWithMarkdownCleanup("""
                {"verdict":"PASS","feedback":"ok","reasoning":"complete"}
                """, ReflexionCritique.class);

        assertEquals(ReflexionVerdict.PASS, critique.getVerdict());
    }

    @Test
    void shouldParseMarkdownFencedJsonAfterCleanup() {
        ReflexionCritique critique = StructuredOutputParsers.parseJsonWithMarkdownCleanup("""
                ```json
                {"verdict":"PASS","feedback":"wrapped","reasoning":"complete"}
                ```
                """, ReflexionCritique.class);

        assertEquals("wrapped", critique.getFeedback());
    }

    @Test
    void shouldThrowForStrictParsingWhenPayloadIsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> StructuredOutputParsers.parseJsonOrThrow(
                "not a json payload",
                ReflexionCritique.class,
                "Invalid reflexion critique payload"
        ));
    }

    @Test
    void shouldReturnNullForNonJsonText() {
        ReflexionCritique critique = StructuredOutputParsers.parseJsonWithMarkdownCleanup(
                "not a json payload",
                ReflexionCritique.class
        );

        assertNull(critique);
    }
}
