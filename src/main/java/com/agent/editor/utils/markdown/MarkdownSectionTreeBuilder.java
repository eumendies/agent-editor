package com.agent.editor.utils.markdown;

import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

// 把 Markdown AST 重建为章节树，供后续“按标题层级优先”的递归分块使用。
public class MarkdownSectionTreeBuilder {

    private final Parser parser = Parser.builder().build();

    public MarkdownSectionDocument build(String markdown) {
        Node document = parser.parse(markdown == null ? "" : markdown);
        List<SectionBuilder> topSections = new ArrayList<>();
        Deque<SectionBuilder> stack = new ArrayDeque<>();
        List<String> leadingBlocks = new ArrayList<>();

        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading) {
                SectionBuilder section = new SectionBuilder(
                        heading.getText().toString().trim(),
                        heading.getLevel(),
                        node.getChars().toString().trim()
                );
                // 维护一个“当前标题路径”栈：遇到同级或更高层标题就回退，遇到更深层标题就挂到当前节点下。
                while (!stack.isEmpty() && stack.peek().headingLevel >= section.headingLevel) {
                    stack.pop();
                }
                if (stack.isEmpty()) {
                    topSections.add(section);
                } else {
                    stack.peek().children.add(section);
                }
                stack.push(section);
                continue;
            }

            String block = node.getChars().toString().trim();
            if (block.isBlank()) {
                continue;
            }
            if (stack.isEmpty()) {
                // 标题前内容不属于任何章节，后续会作为 leadingContent 单独处理。
                leadingBlocks.add(block);
            } else {
                // 非标题节点都归到当前最近的章节；子章节自己的正文会在它成为栈顶后单独收集。
                stack.peek().bodyParts.add(block);
            }
        }

        return new MarkdownSectionDocument(
                joinBlocks(leadingBlocks),
                topSections.stream().map(SectionBuilder::build).toList()
        );
    }

    private String joinBlocks(List<String> blocks) {
        return blocks.stream()
                .filter(block -> !block.isBlank())
                .collect(Collectors.joining("\n\n"))
                .trim();
    }

    private static final class SectionBuilder {
        private final String headingText;
        private final int headingLevel;
        private final String headingLine;
        private final List<String> bodyParts = new ArrayList<>();
        private final List<SectionBuilder> children = new ArrayList<>();

        private SectionBuilder(String headingText, int headingLevel, String headingLine) {
            this.headingText = headingText;
            this.headingLevel = headingLevel;
            this.headingLine = headingLine;
        }

        private MarkdownSectionNode build() {
            // bodyText 只保留“直属正文”，不把子章节正文拍平；是否拼上子章节由 MarkdownSectionNode 决定。
            return new MarkdownSectionNode(
                    headingText,
                    headingLevel,
                    headingLine,
                    bodyParts.stream()
                            .filter(part -> !part.isBlank())
                            .collect(Collectors.joining("\n\n")),
                    children.stream().map(SectionBuilder::build).toList()
            );
        }
    }
}
