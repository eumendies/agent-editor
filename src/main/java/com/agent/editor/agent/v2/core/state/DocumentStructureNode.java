package com.agent.editor.agent.v2.core.state;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class DocumentStructureNode {

    private String nodeId;
    private String path;
    private String headingText;
    private String headingLine;
    private int headingLevel;
    private int childCount;
    private int charLength;
    private int estimatedTokens;
    private boolean leaf;
    private boolean overflow;
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
