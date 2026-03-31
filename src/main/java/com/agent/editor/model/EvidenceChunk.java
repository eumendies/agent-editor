package com.agent.editor.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提供给各个 agent 看的最小证据片段，只保留模型判断是否采信所需的可读信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceChunk {

    private String fileName;
    private String heading;
    private String chunkText;

    public static EvidenceChunk fromRetrieved(RetrievedKnowledgeChunk chunk) {
        return new EvidenceChunk(chunk.getFileName(), chunk.getHeading(), chunk.getChunkText());
    }
}
