package com.agent.editor.service;

import com.agent.editor.agent.core.memory.LongTermMemoryItem;
import com.agent.editor.agent.core.memory.LongTermMemoryType;
import com.agent.editor.agent.tool.memory.MemoryUpsertAction;
import com.agent.editor.agent.tool.memory.MemoryUpsertResult;
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
     * @param documentId 文档作用域；document decision create 必填，replace/delete 传入时必须与既有记忆一致
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
        validateExistingMemoryType(memoryType, existing);
        if (action == MemoryUpsertAction.DELETE) {
            validateDocumentDecisionScope(memoryType, existing, documentId);
            repository.deleteMemory(memoryId);
            return existing;
        }

        validateReplaceInput(memoryType, existing, documentId, summary);
        // replace 先构造新记忆再用同 ID 覆盖写，避免 embedding / 存储失败时把旧记忆提前删掉。
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
        validateExistingMemoryType(memoryType, existing);
        if (memoryType == LongTermMemoryType.DOCUMENT_DECISION) {
            validateDocumentDecisionScope(memoryType, existing, documentId);
        }
    }

    private void validateExistingMemoryType(LongTermMemoryType memoryType, LongTermMemoryItem existing) {
        if (existing.getMemoryType() != memoryType) {
            throw new IllegalArgumentException("memoryType does not match existing memory");
        }
    }

    private void validateDocumentDecisionScope(LongTermMemoryType memoryType,
                                               LongTermMemoryItem existing,
                                               String documentId) {
        if (memoryType != LongTermMemoryType.DOCUMENT_DECISION) {
            return;
        }
        String existingDocumentId = existing.getDocumentId();
        if (existingDocumentId == null || existingDocumentId.isBlank()) {
            throw new IllegalArgumentException("documentId is required for document decisions");
        }
        // 文档决策记忆只能在原文档内改写或删除；传入当前文档时必须严格匹配。
        if (documentId != null && !documentId.isBlank() && !existingDocumentId.equals(documentId)) {
            throw new IllegalArgumentException("documentId does not match existing document decision");
        }
    }

    private String resolveDocumentId(LongTermMemoryItem existing, String documentId) {
        if (existing != null) {
            return existing.getDocumentId();
        }
        return documentId == null || documentId.isBlank() ? null : documentId;
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
