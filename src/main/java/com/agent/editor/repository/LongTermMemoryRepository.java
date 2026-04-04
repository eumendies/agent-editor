package com.agent.editor.repository;

import com.agent.editor.agent.v2.core.memory.LongTermMemoryItem;

import java.util.List;
import java.util.Optional;

/**
 * 已确认长期记忆的持久化边界。
 */
public interface LongTermMemoryRepository {

    default Optional<LongTermMemoryItem> findById(String memoryId) {
        throw new UnsupportedOperationException("Long-term memory lookup is not implemented");
    }

    default LongTermMemoryItem createMemory(LongTermMemoryItem item) {
        throw new UnsupportedOperationException("Long-term memory create is not implemented");
    }

    /**
     * 兼容旧的批量确认链路；新设计里逐条 create 更常用，后续移除 pending 流程后可删除。
     *
     * @param memories 已确认记忆
     */
    default void saveAll(List<LongTermMemoryItem> memories) {
        if (memories == null || memories.isEmpty()) {
            return;
        }
        memories.forEach(this::createMemory);
    }

    default void deleteMemory(String memoryId) {
        throw new UnsupportedOperationException("Long-term memory delete is not implemented");
    }

    /**
     * 读取全部稳定 profile 记忆。
     *
     * @return 已确认的 profile 记忆
     */
    default List<LongTermMemoryItem> listUserProfiles() {
        throw new UnsupportedOperationException("Profile memory lookup is not implemented");
    }

    /**
     * 按文档作用域和查询向量检索历史文档决策。
     *
     * @param documentId 文档标识
     * @param queryVector 查询向量
     * @param topK 返回数量
     * @return 命中的文档决策记忆
     */
    default List<LongTermMemoryItem> searchConfirmedDocumentDecisions(String documentId,
                                                                      float[] queryVector,
                                                                      int topK) {
        throw new UnsupportedOperationException("Document-decision memory search is not implemented");
    }
}
