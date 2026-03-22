package com.agent.editor.utils.rag.markdown;

import com.agent.editor.config.RagProperties;
import com.agent.editor.model.KnowledgeChunk;
import com.agent.editor.utils.rag.SlidingWindowChunker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// Markdown 专用分块器：优先按章节树递归下钻，叶子章节超限时再退回通用滑窗。
public class MarkdownKnowledgeChunkSplitter {

    private final RagProperties properties;
    private final MarkdownSectionTreeBuilder markdownSectionTreeBuilder;
    private final SlidingWindowChunker slidingWindowChunker;

    public MarkdownKnowledgeChunkSplitter(RagProperties properties) {
        this(properties, new MarkdownSectionTreeBuilder(), new SlidingWindowChunker(properties));
    }

    public MarkdownKnowledgeChunkSplitter(RagProperties properties,
                                          MarkdownSectionTreeBuilder markdownSectionTreeBuilder,
                                          SlidingWindowChunker slidingWindowChunker) {
        this.properties = properties;
        this.markdownSectionTreeBuilder = markdownSectionTreeBuilder;
        this.slidingWindowChunker = slidingWindowChunker;
    }

    public void appendChunks(List<KnowledgeChunk> result,
                             AtomicInteger chunkIndex,
                             String documentId,
                             String fileName,
                             String content,
                             Map<String, String> metadata) {
        MarkdownSectionDocument document;
        try {
            document = markdownSectionTreeBuilder.build(content);
        } catch (RuntimeException error) {
            // Markdown 解析失败时不阻断上传，退化成普通滑窗切分，避免整篇内容丢失。
            slidingWindowChunker.appendChunks(result, chunkIndex, documentId, fileName, null, content, metadata);
            return;
        }

        if (!document.leadingContent().isBlank()) {
            // 处理第一个标题前的前置说明，这部分没有稳定 heading，单独作为无标题 chunk 落库。
            slidingWindowChunker.appendChunks(
                    result,
                    chunkIndex,
                    documentId,
                    fileName,
                    null,
                    document.leadingContent(),
                    metadata
            );
        }
        if (document.sections().isEmpty()) {
            slidingWindowChunker.appendChunks(result, chunkIndex, documentId, fileName, null, content, metadata);
            return;
        }
        for (MarkdownSectionNode section : document.sections()) {
            appendSectionChunks(result, chunkIndex, documentId, fileName, section, List.of(), metadata);
        }
    }

    private void appendSectionChunks(List<KnowledgeChunk> result,
                                     AtomicInteger chunkIndex,
                                     String documentId,
                                     String fileName,
                                     MarkdownSectionNode section,
                                     List<String> headingPath,
                                     Map<String, String> metadata) {
        List<String> currentPath = new ArrayList<>(headingPath);
        currentPath.add(section.headingText());
        String sectionText = section.fullText();
        String heading = String.join(" > ", currentPath);

        if (sectionText.length() <= properties.chunkSize()) {
            result.add(new KnowledgeChunk(
                    documentId,
                    chunkIndex.getAndIncrement(),
                    fileName,
                    heading,
                    sectionText,
                    metadata
            ));
            return;
        }
        // 父章节超限时，先保留当前章节自己的前言，再继续递归子章节，避免父级介绍信息丢失。
        if (!section.introText().equals(section.headingLine()) && !section.introText().isBlank()) {
            slidingWindowChunker.appendChunks(
                    result,
                    chunkIndex,
                    documentId,
                    fileName,
                    heading,
                    section.introText(),
                    metadata
            );
        }
        if (!section.children().isEmpty()) {
            // 有子章节时优先继续按结构下钻，不生成一个覆盖整棵子树的大 chunk，减少父子内容重复。
            for (MarkdownSectionNode child : section.children()) {
                appendSectionChunks(result, chunkIndex, documentId, fileName, child, currentPath, metadata);
            }
            return;
        }
        // 已经是叶子章节且仍然超限，只能退回滑窗，保证 chunkSize 是硬上限。
        slidingWindowChunker.appendChunks(result, chunkIndex, documentId, fileName, heading, sectionText, metadata);
    }
}
