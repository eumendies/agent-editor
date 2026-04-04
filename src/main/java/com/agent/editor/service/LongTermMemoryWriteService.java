package com.agent.editor.service;

import com.agent.editor.agent.v2.core.memory.LongTermMemoryItem;
import com.agent.editor.agent.v2.core.memory.LongTermMemoryType;
import com.agent.editor.agent.v2.tool.memory.MemoryUpsertAction;
import com.agent.editor.agent.v2.tool.memory.MemoryUpsertResult;
import com.agent.editor.repository.LongTermMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 长期记忆写入入口。
 * 它统一处理 agent 与 UI 的写入动作，确保 create / replace / delete 语义在应用层保持一致。
 */
@Service
public class LongTermMemoryWriteService {

    private static final String DEFAULT_PROFILE_SCOPE = "default";

    private final LongTermMemoryRepository repository;
    private final KnowledgeEmbeddingService embeddingService;
    private final Supplier<String> memoryIdSupplier;

    @Autowired
    public LongTermMemoryWriteService(ObjectProvider<LongTermMemoryRepository> repositoryProvider,
                                      KnowledgeEmbeddingService embeddingService) {
        this(repositoryProvider.getIfAvailable(), embeddingService, () -> UUID.randomUUID().toString());
    }

    LongTermMemoryWriteService(LongTermMemoryRepository repository,
                               KnowledgeEmbeddingService embeddingService,
                               Supplier<String> memoryIdSupplier) {
        this.repository = repository;
        this.embeddingService = embeddingService;
        this.memoryIdSupplier = memoryIdSupplier;
    }

    /**
     * 根据给定动作执行长期记忆写入。
     *
     * @param action create / replace / delete 动作
     * @param memoryType 长期记忆类型
     * @param memoryId 目标记忆 ID；replace/delete 必填
     * @param documentId 文档作用域；document decision create 必填
     * @param summary 记忆摘要；create/replace 必填
     * @return 持久化后的记忆对象；delete 返回被删除的旧对象
     */
    public LongTermMemoryItem upsert(MemoryUpsertAction action,
                                     LongTermMemoryType memoryType,
                                     String memoryId,
                                     String documentId,
                                     String summary) {
        if (repository == null) {
            throw new IllegalStateException("Long-term memory write is not configured");
        }
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        if (memoryType == null) {
            throw new IllegalArgumentException("memoryType is required");
        }
        if (action == MemoryUpsertAction.CREATE) {
            validateCreateInput(memoryType, documentId, summary);
            return repository.createMemory(newMemory(memoryIdSupplier.get(), memoryType, documentId, summary));
        }

        if (memoryId == null || memoryId.isBlank()) {
            throw new IllegalArgumentException("memoryId is required for replace/delete");
        }
        LongTermMemoryItem existing = repository.findById(memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Long-term memory not found: " + memoryId));
        if (action == MemoryUpsertAction.DELETE) {
            repository.deleteMemory(memoryId);
            return existing;
        }

        validateReplaceInput(memoryType, existing, documentId, summary);
        // replace 采用 delete + insert，由应用层明确控制语义，避免把“覆盖旧记忆”的规则藏进底层存储实现里。
        repository.deleteMemory(memoryId);
        return repository.createMemory(newMemory(memoryId, memoryType, resolveDocumentId(existing, documentId), summary));
    }

    public MemoryUpsertResult upsertResult(MemoryUpsertAction action,
                                           String memoryType,
                                           String memoryId,
                                           String documentId,
                                           String summary) {
        LongTermMemoryItem item = upsert(
                action,
                LongTermMemoryType.valueOf(memoryType),
                memoryId,
                documentId,
                summary
        );
        return new MemoryUpsertResult(
                action.name(),
                item.getMemoryId(),
                item.getMemoryType().name(),
                item.getDocumentId(),
                item.getSummary()
        );
    }

    private void validateCreateInput(LongTermMemoryType memoryType, String documentId, String summary) {
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary is required for create/replace");
        }
        if (memoryType == LongTermMemoryType.DOCUMENT_DECISION && (documentId == null || documentId.isBlank())) {
            throw new IllegalArgumentException("documentId is required for document decisions");
        }
    }

    private void validateReplaceInput(LongTermMemoryType memoryType,
                                      LongTermMemoryItem existing,
                                      String documentId,
                                      String summary) {
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary is required for create/replace");
        }
        if (existing.getMemoryType() != memoryType) {
            throw new IllegalArgumentException("memoryType does not match existing memory");
        }
        if (memoryType == LongTermMemoryType.DOCUMENT_DECISION) {
            String effectiveDocumentId = resolveDocumentId(existing, documentId);
            if (effectiveDocumentId == null || effectiveDocumentId.isBlank()) {
                throw new IllegalArgumentException("documentId is required for document decisions");
            }
        }
    }

    private String resolveDocumentId(LongTermMemoryItem existing, String documentId) {
        return documentId == null || documentId.isBlank() ? existing == null ? null : existing.getDocumentId() : documentId;
    }

    private LongTermMemoryItem newMemory(String memoryId,
                                         LongTermMemoryType memoryType,
                                         String documentId,
                                         String summary) {
        LocalDateTime now = LocalDateTime.now();
        String normalizedDocumentId = memoryType == LongTermMemoryType.DOCUMENT_DECISION ? documentId : null;
        return new LongTermMemoryItem(
                memoryId,
                memoryType,
                memoryType == LongTermMemoryType.USER_PROFILE ? DEFAULT_PROFILE_SCOPE : normalizedDocumentId,
                normalizedDocumentId,
                summary,
                summary,
                null,
                null,
                List.of(),
                now,
                now,
                embeddingService.embed(summary)
        );
    }
}
