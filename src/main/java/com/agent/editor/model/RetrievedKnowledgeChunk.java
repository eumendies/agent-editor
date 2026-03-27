package com.agent.editor.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrievedKnowledgeChunk {

    private String documentId;
    private String fileName;
    private int chunkIndex;
    private String heading;
    private String chunkText;
    private double score;
}
