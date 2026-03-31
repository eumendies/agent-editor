package com.agent.editor.agent.v2.supervisor.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EvidenceContractsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeResearcherSummary() throws Exception {
        ResearcherSummary summary = objectMapper.readValue("""
                {
                  "evidenceSummary":"supports supervisor and reflexion",
                  "limitations":"no metrics",
                  "uncoveredPoints":["online performance"]
                }
                """, ResearcherSummary.class);

        assertEquals("supports supervisor and reflexion", summary.getEvidenceSummary());
        assertEquals("no metrics", summary.getLimitations());
        assertEquals(List.of("online performance"), summary.getUncoveredPoints());
    }

    @Test
    void shouldDeserializeEvidencePackage() throws Exception {
        EvidencePackage evidence = objectMapper.readValue("""
                {
                  "queries":["agentic rag","hybrid supervisor"],
                  "evidenceSummary":"supports supervisor and reflexion",
                  "limitations":"no metrics",
                  "uncoveredPoints":["online performance"],
                  "chunks":[
                    {
                      "fileName":"resume.md",
                      "heading":"项目经历",
                      "chunkText":"支持 supervisor"
                    }
                  ]
                }
                """, EvidencePackage.class);

        assertEquals(List.of("online performance"), evidence.getUncoveredPoints());
        assertEquals(1, evidence.getChunks().size());
        String serialized = objectMapper.writeValueAsString(evidence);
        assertFalse(serialized.contains("documentId"));
        assertFalse(serialized.contains("chunkIndex"));
        assertFalse(serialized.contains("score"));
    }

    @Test
    void shouldDeserializeReviewerFeedback() throws Exception {
        ReviewerFeedback feedback = objectMapper.readValue("""
                {
                  "verdict":"REVISE",
                  "instructionSatisfied":false,
                  "evidenceGrounded":false,
                  "unsupportedClaims":["Latency improved by 40%"],
                  "missingRequirements":["Explain project value"],
                  "feedback":"Remove unsupported metric and explain impact.",
                  "reasoning":"The metric is not in evidence and the answer is incomplete."
                }
                """, ReviewerFeedback.class);

        assertEquals(ReviewerVerdict.REVISE, feedback.getVerdict());
        assertFalse(feedback.isInstructionSatisfied());
        assertFalse(feedback.isEvidenceGrounded());
        assertEquals(List.of("Latency improved by 40%"), feedback.getUnsupportedClaims());
        assertEquals(List.of("Explain project value"), feedback.getMissingRequirements());
    }
}
