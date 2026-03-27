package com.agent.editor.utils.rag;

import com.agent.editor.config.RagProperties;
import com.agent.editor.model.KnowledgeChunk;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 通用文本滑窗切分器。
 * 当内容没有可用的章节树，或者叶子章节仍然超过 chunkSize 时，最终都退回到这里。
 */
public class SlidingWindowChunker {

    private final RagProperties properties;

    public SlidingWindowChunker(RagProperties properties) {
        this.properties = properties;
    }

    public void appendChunks(List<KnowledgeChunk> result,
                             AtomicInteger chunkIndex,
                             String documentId,
                             String fileName,
                             String heading,
                             String text,
                             Map<String, String> metadata) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isBlank()) {
            return;
        }
        if (normalized.length() <= properties.getChunkSize()) {
            addChunk(result, chunkIndex, documentId, fileName, heading, normalized, metadata);
            return;
        }

        // overlap 被限制在 [0, chunkSize) 内，避免出现不前进或越界的窗口。
        int overlap = Math.max(0, Math.min(properties.getChunkOverlap(), properties.getChunkSize() - 1));
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + properties.getChunkSize());
            String chunkText = normalized.substring(start, end).trim();
            if (!chunkText.isBlank()) {
                addChunk(result, chunkIndex, documentId, fileName, heading, chunkText, metadata);
            }
            if (end >= normalized.length()) {
                break;
            }
            // 下一窗从“当前窗尾 - overlap”继续，既保留上下文，又保证窗口单调向前推进。
            start = Math.max(start + 1, end - overlap);
        }
    }

    private void addChunk(List<KnowledgeChunk> result,
                          AtomicInteger chunkIndex,
                          String documentId,
                          String fileName,
                          String heading,
                          String chunkText,
                          Map<String, String> metadata) {
        result.add(new KnowledgeChunk(
                documentId,
                chunkIndex.getAndIncrement(),
                fileName,
                heading,
                chunkText,
                metadata
        ));
    }
}
