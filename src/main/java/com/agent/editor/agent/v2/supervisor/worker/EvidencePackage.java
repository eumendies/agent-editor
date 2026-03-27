package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.model.RetrievedKnowledgeChunk;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvidencePackage {

    private List<String> queries;
    private String evidenceSummary;
    private String limitations;
    private List<String> uncoveredPoints;
    private List<RetrievedKnowledgeChunk> chunks;
}
