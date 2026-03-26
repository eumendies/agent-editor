package com.agent.editor.agent.v2.supervisor.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EvidenceContractsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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
                      "documentId":"doc-1",
                      "fileName":"resume.md",
                      "chunkIndex":1,
                      "heading":"项目经历",
                      "chunkText":"支持 supervisor",
                      "score":0.91
                    }
                  ]
                }
                """, EvidencePackage.class);

        assertEquals(List.of("online performance"), evidence.uncoveredPoints());
        assertEquals(1, evidence.chunks().size());
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

        assertEquals(ReviewerVerdict.REVISE, feedback.verdict());
        assertFalse(feedback.instructionSatisfied());
        assertFalse(feedback.evidenceGrounded());
        assertEquals(List.of("Latency improved by 40%"), feedback.unsupportedClaims());
        assertEquals(List.of("Explain project value"), feedback.missingRequirements());
    }
}
