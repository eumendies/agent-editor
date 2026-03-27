package com.agent.editor.agent.v2.supervisor.worker;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewerFeedback {

    private ReviewerVerdict verdict;
    private boolean instructionSatisfied;
    private boolean evidenceGrounded;
    private List<String> unsupportedClaims;
    private List<String> missingRequirements;
    private String feedback;
    private String reasoning;
}
