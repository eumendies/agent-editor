package com.agent.editor.agent.v2.core.state;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class DocumentStructureSnapshot {

    private String documentId;
    private String documentVersion;
    private String title;
    private List<DocumentStructureNode> nodes = List.of();
    private int estimatedTokens;
    private int oversizedNodeCount;

    public DocumentStructureSnapshot(String documentId,
                                     String documentVersion,
                                     String title,
                                     List<DocumentStructureNode> nodes,
                                     int estimatedTokens,
                                     int oversizedNodeCount) {
        this.documentId = documentId;
        this.documentVersion = documentVersion;
        this.title = title;
        setNodes(nodes);
        this.estimatedTokens = estimatedTokens;
        this.oversizedNodeCount = oversizedNodeCount;
    }

    public void setNodes(List<DocumentStructureNode> nodes) {
        this.nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }
}
