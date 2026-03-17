package com.agent.editor.agent.v2.reflexion;

public record ReflexionCritique(
        ReflexionVerdict verdict,
        String feedback,
        String reasoning
) {
}
