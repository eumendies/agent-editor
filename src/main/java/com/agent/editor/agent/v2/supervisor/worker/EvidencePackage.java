package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.model.RetrievedKnowledgeChunk;

import java.util.List;

public record EvidencePackage(
        List<String> queries,
        String evidenceSummary,
        String limitations,
        List<String> uncoveredPoints,
        List<RetrievedKnowledgeChunk> chunks
) {
}
