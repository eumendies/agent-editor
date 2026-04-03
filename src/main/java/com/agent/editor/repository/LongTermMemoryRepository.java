package com.agent.editor.repository;

import com.agent.editor.agent.v2.core.memory.LongTermMemoryItem;

import java.util.List;

/**
 * 已确认长期记忆的持久化边界。
 */
public interface LongTermMemoryRepository {

    /**
     * 保存一批已确认的长期记忆。
     *
     * @param memories 已确认记忆
     */
    default void saveAll(List<LongTermMemoryItem> memories) {
        throw new UnsupportedOperationException("Long-term memory persistence is not implemented");
    }

    /**
     * 按 profile 作用域读取稳定偏好。
     *
     * @param scopeKey profile 作用域键
     * @return 已确认的 profile 记忆
     */
    default List<LongTermMemoryItem> findConfirmedProfiles(String scopeKey) {
        throw new UnsupportedOperationException("Profile memory lookup is not implemented");
    }

    /**
     * 按文档作用域和查询向量检索历史任务决策。
     *
     * @param documentId 文档标识
     * @param queryVector 查询向量
     * @param topK 返回数量
     * @return 命中的任务决策记忆
     */
    default List<LongTermMemoryItem> searchConfirmedTaskDecisions(String documentId,
                                                                  float[] queryVector,
                                                                  int topK) {
        throw new UnsupportedOperationException("Task-decision memory search is not implemented");
    }
}
