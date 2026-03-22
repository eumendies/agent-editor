package com.agent.editor.utils;

import com.agent.editor.config.RagProperties;
import com.agent.editor.model.KnowledgeChunk;
import com.agent.editor.utils.markdown.MarkdownKnowledgeChunkSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 通用分块门面。
 * 这里只负责识别文档类型并把请求分发给具体实现：
 * Markdown 走章节树递归分块，其他文本走通用滑窗分块。
 */
@Component
public class KnowledgeChunkSplitter {

    private final SlidingWindowChunker slidingWindowChunker;
    private final MarkdownKnowledgeChunkSplitter markdownKnowledgeChunkSplitter;

    @Autowired
    public KnowledgeChunkSplitter(RagProperties properties) {
        this(new SlidingWindowChunker(properties), new MarkdownKnowledgeChunkSplitter(properties));
    }

    KnowledgeChunkSplitter(SlidingWindowChunker slidingWindowChunker,
                           MarkdownKnowledgeChunkSplitter markdownKnowledgeChunkSplitter) {
        this.slidingWindowChunker = slidingWindowChunker;
        this.markdownKnowledgeChunkSplitter = markdownKnowledgeChunkSplitter;
    }

    public List<KnowledgeChunk> split(String documentId,
                                      String fileName,
                                      String content,
                                      Map<String, String> metadata) {
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isBlank()) {
            return List.of();
        }

        List<KnowledgeChunk> result = new ArrayList<>();
        AtomicInteger chunkIndex = new AtomicInteger();
        if (isMarkdown(fileName, metadata)) {
            // Markdown 有稳定的标题层级，优先按结构分块，尽量保留章节语义。
            markdownKnowledgeChunkSplitter.appendChunks(
                    result,
                    chunkIndex,
                    documentId,
                    fileName,
                    normalizedContent,
                    metadata
            );
        } else {
            // 其他文档类型没有统一的章节树语义，直接退回通用滑窗切分。
            slidingWindowChunker.appendChunks(
                    result,
                    chunkIndex,
                    documentId,
                    fileName,
                    null,
                    normalizedContent,
                    metadata
            );
        }
        return result;
    }

    private boolean isMarkdown(String fileName, Map<String, String> metadata) {
        String normalizedFileName = fileName == null ? "" : fileName.toLowerCase();
        if (normalizedFileName.endsWith(".md") || normalizedFileName.endsWith(".markdown")) {
            return true;
        }
        // 上传链路里 parser 也会写 documentType，这里做双保险，避免文件名缺失时丢掉 markdown 结构信息。
        String documentType = metadata == null ? null : metadata.get("documentType");
        return documentType != null && "markdown".equalsIgnoreCase(documentType);
    }
}
