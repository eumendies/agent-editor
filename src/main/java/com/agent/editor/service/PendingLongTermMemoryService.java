package com.agent.editor.service;

import com.agent.editor.agent.v2.core.memory.PendingLongTermMemoryItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PendingLongTermMemoryService {

    private final Map<String, List<PendingLongTermMemoryItem>> pendingMemoriesByTask = new ConcurrentHashMap<>();

    public List<PendingLongTermMemoryItem> savePendingCandidates(String taskId,
                                                                 List<PendingLongTermMemoryItem> candidates) {
        List<PendingLongTermMemoryItem> safeCandidates = candidates == null ? List.of() : List.copyOf(candidates);
        // 每个 task 只保留最新一版候选，避免用户确认时混入旧轮次提取结果。
        pendingMemoriesByTask.put(taskId, safeCandidates);
        return safeCandidates;
    }

    public List<PendingLongTermMemoryItem> getPendingCandidates(String taskId) {
        return pendingMemoriesByTask.get(taskId);
    }

    public List<PendingLongTermMemoryItem> confirmCandidates(String taskId, List<String> candidateIds) {
        List<PendingLongTermMemoryItem> existing = pendingMemoriesByTask.get(taskId);
        if (existing == null || existing.isEmpty()) {
            return List.of();
        }
        List<String> selectedIds = candidateIds == null ? List.of() : List.copyOf(candidateIds);
        List<PendingLongTermMemoryItem> confirmed = existing.stream()
                .filter(item -> selectedIds.contains(item.getCandidateId()))
                .toList();
        List<PendingLongTermMemoryItem> remaining = existing.stream()
                .filter(item -> !selectedIds.contains(item.getCandidateId()))
                .toList();

        // 确认后立即从 pending 区移除，保证同一候选不会被重复确认。
        if (remaining.isEmpty()) {
            pendingMemoriesByTask.remove(taskId);
        } else {
            pendingMemoriesByTask.put(taskId, remaining);
        }
        return confirmed;
    }

    public List<PendingLongTermMemoryItem> discardPendingCandidates(String taskId) {
        return pendingMemoriesByTask.remove(taskId);
    }

    public List<PendingLongTermMemoryItem> discardCandidates(String taskId, List<String> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            List<PendingLongTermMemoryItem> removed = pendingMemoriesByTask.remove(taskId);
            return removed == null ? List.of() : removed;
        }
        List<PendingLongTermMemoryItem> existing = pendingMemoriesByTask.get(taskId);
        if (existing == null || existing.isEmpty()) {
            return List.of();
        }
        List<String> selectedIds = List.copyOf(candidateIds);
        List<PendingLongTermMemoryItem> discarded = existing.stream()
                .filter(item -> selectedIds.contains(item.getCandidateId()))
                .toList();
        List<PendingLongTermMemoryItem> remaining = existing.stream()
                .filter(item -> !selectedIds.contains(item.getCandidateId()))
                .toList();
        if (remaining.isEmpty()) {
            pendingMemoriesByTask.remove(taskId);
        } else {
            pendingMemoriesByTask.put(taskId, remaining);
        }
        return discarded;
    }
}
