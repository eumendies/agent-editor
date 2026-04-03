package com.agent.editor.agent.v2.core.memory;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 待用户确认的长期记忆候选。
 * 它在确认前只存在于临时存储中，避免未审核内容污染长期记忆库。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PendingLongTermMemoryItem extends LongTermMemoryItem {

    private String candidateId;

    public PendingLongTermMemoryItem(String candidateId, LongTermMemoryItem item) {
        this.candidateId = candidateId;
        if (item == null) {
            return;
        }
        setMemoryId(item.getMemoryId());
        setMemoryType(item.getMemoryType());
        setScopeKey(item.getScopeKey());
        setDocumentId(item.getDocumentId());
        setSummary(item.getSummary());
        setDetails(item.getDetails());
        setSourceTaskId(item.getSourceTaskId());
        setSourceSessionId(item.getSourceSessionId());
        setTags(item.getTags());
        setCreatedAt(item.getCreatedAt());
        setUpdatedAt(item.getUpdatedAt());
        setEmbedding(item.getEmbedding());
    }
}
