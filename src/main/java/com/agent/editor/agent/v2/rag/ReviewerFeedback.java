package com.agent.editor.agent.v2.rag;

import java.util.List;

public record ReviewerFeedback(
        ReviewerVerdict verdict,
        boolean instructionSatisfied,
        boolean evidenceGrounded,
        List<String> unsupportedClaims,
        List<String> missingRequirements,
        String feedback,
        String reasoning
) {
}
