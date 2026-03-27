package com.agent.editor.utils.rag.markdown;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class MarkdownSectionDocument {

    private String leadingContent = "";
    private List<MarkdownSectionNode> sections = List.of();

    public MarkdownSectionDocument(String leadingContent, List<MarkdownSectionNode> sections) {
        setLeadingContent(leadingContent);
        setSections(sections);
    }

    public void setLeadingContent(String leadingContent) {
        this.leadingContent = leadingContent == null ? "" : leadingContent.trim();
    }

    public void setSections(List<MarkdownSectionNode> sections) {
        this.sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
