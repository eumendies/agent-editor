package com.agent.editor.utils.rag.markdown;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

// 一个章节节点只保存本标题、直属正文和子章节，是否展开整棵子树由调用方决定。
@Data
@NoArgsConstructor
public class MarkdownSectionNode {

    private String headingText = "";
    private int headingLevel;
    private String headingLine = "";
    private String bodyText = "";
    private List<MarkdownSectionNode> children = List.of();

    public MarkdownSectionNode(String headingText,
                               int headingLevel,
                               String headingLine,
                               String bodyText,
                               List<MarkdownSectionNode> children) {
        setHeadingText(headingText);
        this.headingLevel = headingLevel;
        setHeadingLine(headingLine);
        setBodyText(bodyText);
        setChildren(children);
    }

    public void setHeadingText(String headingText) {
        this.headingText = headingText == null ? "" : headingText.trim();
    }

    public void setHeadingLine(String headingLine) {
        this.headingLine = headingLine == null ? "" : headingLine.trim();
    }

    public void setBodyText(String bodyText) {
        this.bodyText = bodyText == null ? "" : bodyText.trim();
    }

    public void setChildren(List<MarkdownSectionNode> children) {
        this.children = children == null ? List.of() : List.copyOf(children);
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

    public String render() {
        List<String> parts = new ArrayList<>();
        if (!headingLine.isBlank()) {
            parts.add(headingLine);
        }
        if (!bodyText.isBlank()) {
            parts.add(bodyText);
        }
        children.stream()
                .map(MarkdownSectionNode::render)
                .filter(text -> !text.isBlank())
                .forEach(parts::add);
        return String.join("\n\n", parts).trim();
    }
}
