package com.agent.editor.agent.core.memory;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 已确认或待确认的长期记忆条目。
 * 它表达 agent 在多次任务之间可以复用的稳定信息，而不是原始 transcript。
 */
@Data
@NoArgsConstructor
public class LongTermMemoryItem {

    private String memoryId;
    private LongTermMemoryType memoryType;
    private String documentId;
    private String summary;
    private String details;
    private String sourceTaskId;
    private String sourceSessionId;
    private List<String> tags = List.of();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private float[] embedding = new float[0];

    public LongTermMemoryItem(String memoryId,
                              LongTermMemoryType memoryType,
                              String documentId,
                              String summary,
                              String details,
                              String sourceTaskId,
                              String sourceSessionId,
                              List<String> tags,
                              LocalDateTime createdAt,
                              LocalDateTime updatedAt,
                              float[] embedding) {
        this.memoryId = memoryId;
        this.memoryType = memoryType;
        this.documentId = documentId;
        this.summary = summary;
        this.details = details;
        this.sourceTaskId = sourceTaskId;
        this.sourceSessionId = sourceSessionId;
        setTags(tags);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        setEmbedding(embedding);
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding == null ? new float[0] : embedding.clone();
    }

    public float[] getEmbedding() {
        return embedding == null ? new float[0] : embedding.clone();
    }
}
