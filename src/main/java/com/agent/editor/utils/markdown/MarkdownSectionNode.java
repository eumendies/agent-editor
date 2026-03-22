package com.agent.editor.utils.markdown;

import java.util.ArrayList;
import java.util.List;

// 一个章节节点只保存本标题、直属正文和子章节，是否展开整棵子树由调用方决定。
public record MarkdownSectionNode(
        String headingText,
        int headingLevel,
        String headingLine,
        String bodyText,
        List<MarkdownSectionNode> children
) {

    public MarkdownSectionNode {
        // 在节点层做一次规范化，确保树遍历时拿到的都是稳定字符串和不可变 children。
        headingText = headingText == null ? "" : headingText.trim();
        headingLine = headingLine == null ? "" : headingLine.trim();
        bodyText = bodyText == null ? "" : bodyText.trim();
        children = children == null ? List.of() : List.copyOf(children);
    }

    public String fullText() {
        List<String> parts = new ArrayList<>();
        if (!headingLine.isBlank()) {
            parts.add(headingLine);
        }
        if (!bodyText.isBlank()) {
            parts.add(bodyText);
        }
        children.stream()
                .map(MarkdownSectionNode::fullText)
                .filter(text -> !text.isBlank())
                .forEach(parts::add);
        // fullText 表示“当前章节完整语义范围”，包含当前标题、直属正文和全部子章节。
        return String.join("\n\n", parts).trim();
    }

    public String introText() {
        List<String> parts = new ArrayList<>();
        if (!headingLine.isBlank()) {
            parts.add(headingLine);
        }
        if (!bodyText.isBlank()) {
            parts.add(bodyText);
        }
        // introText 只保留当前章节自己的标题和前言，用于父章节超限时先落一份父级介绍。
        return String.join("\n\n", parts).trim();
    }
}
