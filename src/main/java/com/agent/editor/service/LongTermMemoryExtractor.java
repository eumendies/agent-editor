package com.agent.editor.service;

import com.agent.editor.agent.v2.core.memory.ExecutionMemory;
import com.agent.editor.agent.v2.core.memory.LongTermMemoryItem;
import com.agent.editor.agent.v2.core.memory.LongTermMemoryType;
import com.agent.editor.agent.v2.core.memory.PendingLongTermMemoryItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LongTermMemoryExtractor {

    private final KnowledgeEmbeddingService embeddingService;

    public LongTermMemoryExtractor() {
        this(null);
    }

    @Autowired
    public LongTermMemoryExtractor(KnowledgeEmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    public List<PendingLongTermMemoryItem> extractCandidates(String taskId,
                                                             String sessionId,
                                                             String documentId,
                                                             String instruction,
                                                             ExecutionMemory memory,
                                                             String finalContent) {
        List<PendingLongTermMemoryItem> candidates = new ArrayList<>();
        if (instruction == null || instruction.isBlank()) {
            return candidates;
        }
        String normalized = instruction.trim();
        String lower = normalized.toLowerCase();

        // 只抽带“长期生效”语义的表达，避免普通任务描述误沉淀为 user profile。
        if (looksLikeStablePreference(lower, normalized)) {
            candidates.add(candidate(LongTermMemoryType.USER_PROFILE,
                    "default",
                    null,
                    normalized,
                    taskId,
                    sessionId));
        }

        // document decision 只抽明确的保留/禁止/约束型指令，避免把一般编辑动作误当成长期决策。
        if (looksLikeExplicitDecision(lower, normalized) && documentId != null && !documentId.isBlank()) {
            candidates.add(candidate(LongTermMemoryType.DOCUMENT_DECISION,
                    documentId,
                    documentId,
                    normalized,
                    taskId,
                    sessionId));
        }
        return candidates;
    }

    private PendingLongTermMemoryItem candidate(LongTermMemoryType memoryType,
                                                String scopeKey,
                                                String documentId,
                                                String summary,
                                                String taskId,
                                                String sessionId) {
        String memoryId = "memory-" + UUID.randomUUID();
        String candidateId = "candidate-" + UUID.randomUUID();
        LongTermMemoryItem item = new LongTermMemoryItem(
                memoryId,
                memoryType,
                scopeKey,
                documentId,
                summary,
                summary,
                taskId,
                sessionId,
                List.of("candidate"),
                LocalDateTime.now(),
                LocalDateTime.now(),
                embedding(summary)
        );
        return new PendingLongTermMemoryItem(candidateId, item);
    }

    private float[] embedding(String summary) {
        if (embeddingService == null) {
            return new float[]{0.0f};
        }
        return embeddingService.embed(summary);
    }

    private boolean looksLikeStablePreference(String lower, String original) {
        return lower.contains("always ")
                || lower.contains("default ")
                || lower.contains("from now on")
                || original.contains("默认")
                || original.contains("以后")
                || original.contains("始终");
    }

    private boolean looksLikeExplicitDecision(String lower, String original) {
        return lower.startsWith("keep ")
                || lower.contains("do not ")
                || lower.contains("don't ")
                || lower.contains("retain ")
                || lower.contains("preserve ")
                || original.contains("保留")
                || original.contains("不要")
                || original.contains("不改");
    }
}
