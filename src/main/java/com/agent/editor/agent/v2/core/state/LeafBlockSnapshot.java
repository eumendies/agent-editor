package com.agent.editor.agent.v2.core.state;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LeafBlockSnapshot {

    private String nodeId;
    private String blockId;
    private int ordinal;
    private int startOffset;
    private int endOffset;
    private int charLength;
    private int estimatedTokens;
    private String hash;
    private String summary;

    public LeafBlockSnapshot(String nodeId,
                             String blockId,
                             int ordinal,
                             int startOffset,
                             int endOffset,
                             int charLength,
                             int estimatedTokens,
                             String hash,
                             String summary) {
        this.nodeId = nodeId;
        this.blockId = blockId;
        this.ordinal = ordinal;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.charLength = charLength;
        this.estimatedTokens = estimatedTokens;
        this.hash = hash;
        this.summary = summary;
    }
}
