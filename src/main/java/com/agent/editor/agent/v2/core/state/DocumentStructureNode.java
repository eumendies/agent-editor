package com.agent.editor.agent.v2.core.state;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档结构树中的单个节点快照，表示一个标题及其子树的聚合信息。
 */
@Data
@NoArgsConstructor
public class DocumentStructureNode {

    // 节点唯一标识，通常与结构化解析结果保持一致。
    private String nodeId;
    // 节点在结构树中的路径，便于定位层级。
    private String path;
    // 当前标题的纯文本内容。
    private String headingText;
    // 标题对应的原始行文本。
    private String headingLine;
    // 标题层级，例如 h1/h2 对应的深度。
    private int headingLevel;
    // 直接子节点数量。
    private int childCount;
    // 当前节点覆盖正文的大致字符数。
    private int charLength;
    // 当前节点覆盖内容估算出的 token 数。
    private int estimatedTokens;
    // 是否已经是结构树的叶子节点。
    private boolean leaf;
    // 是否超过了后续处理允许的大小阈值。
    private boolean overflow;
    // 子节点快照列表。
    private List<DocumentStructureNode> children = List.of();

    public DocumentStructureNode(String nodeId,
                                 String path,
                                 String headingText,
                                 String headingLine,
                                 int headingLevel,
                                 int childCount,
                                 int charLength,
                                 int estimatedTokens,
                                 boolean leaf,
                                 boolean overflow,
                                 List<DocumentStructureNode> children) {
        this.nodeId = nodeId;
        this.path = path;
        this.headingText = headingText;
        this.headingLine = headingLine;
        this.headingLevel = headingLevel;
        this.childCount = childCount;
        this.charLength = charLength;
        this.estimatedTokens = estimatedTokens;
        this.leaf = leaf;
        this.overflow = overflow;
        setChildren(children);
    }

    public void setChildren(List<DocumentStructureNode> children) {
        this.children = children == null ? List.of() : List.copyOf(children);
    }
}
