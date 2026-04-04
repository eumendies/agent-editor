package com.agent.editor.agent.v2.memory;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class LongTermMemoryExtractionResponse {

    private List<MemoryCandidateSummary> userProfiles = List.of();
    private List<MemoryCandidateSummary> documentDecisions = List.of();

    public void setUserProfiles(List<MemoryCandidateSummary> userProfiles) {
        this.userProfiles = userProfiles == null ? List.of() : List.copyOf(userProfiles);
    }

    public void setDocumentDecisions(List<MemoryCandidateSummary> documentDecisions) {
        this.documentDecisions = documentDecisions == null ? List.of() : List.copyOf(documentDecisions);
    }
}
