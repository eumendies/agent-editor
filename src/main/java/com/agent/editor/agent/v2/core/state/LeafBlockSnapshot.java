package com.agent.editor.agent.v2.core.state;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 叶子节点切分后的内容块快照，用于增量比较和分块处理。
 */
@Data
@NoArgsConstructor
public class LeafBlockSnapshot {

    // 所属结构节点 ID。
    private String nodeId;
    // 当前块的唯一标识。
    private String blockId;
    // 在所属节点内的顺序号。
    private int ordinal;
    // 当前块在原文中的起始偏移量。
    private int startOffset;
    // 当前块在原文中的结束偏移量。
    private int endOffset;
    // 当前块的字符长度。
    private int charLength;
    // 当前块估算出的 token 数。
    private int estimatedTokens;
    // 内容哈希，用于识别块是否变化。
    private String hash;
    // 面向上层调度或压缩的块摘要。
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
