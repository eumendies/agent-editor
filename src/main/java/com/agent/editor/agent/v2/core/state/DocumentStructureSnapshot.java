package com.agent.editor.agent.v2.core.state;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档结构化分析后的整体快照，供规划、裁剪和分块策略复用。
 */
@Data
@NoArgsConstructor
public class DocumentStructureSnapshot {

    // 被分析文档的唯一标识。
    private String documentId;
    // 结构快照对应的文档版本号。
    private String documentVersion;
    // 文档标题，便于在多文档上下文中辨识。
    private String title;
    // 顶层结构节点列表。
    private List<DocumentStructureNode> nodes = List.of();
    // 整个结构快照估算出的总 token 数。
    private int estimatedTokens;
    // 超过阈值、需要进一步切分的节点数量。
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
