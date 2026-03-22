package com.agent.editor.utils.markdown;

import java.util.List;

// 章节树构建结果：包含标题前正文和所有顶层章节。
public record MarkdownSectionDocument(
        String leadingContent,
        List<MarkdownSectionNode> sections
) {

    public MarkdownSectionDocument {
        // 统一在入口做标准化，避免后续分块逻辑重复判空和复制集合。
        leadingContent = leadingContent == null ? "" : leadingContent.trim();
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
