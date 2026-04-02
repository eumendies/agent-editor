package com.agent.editor.service;

import com.agent.editor.dto.DiffResult;
import com.agent.editor.dto.PendingDocumentChange;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PendingDocumentChangeService {

    private final DiffService diffService;
    private final Map<String, PendingDocumentChange> pendingChanges = new ConcurrentHashMap<>();

    public PendingDocumentChangeService(DiffService diffService) {
        this.diffService = diffService;
    }

    public PendingDocumentChange savePendingChange(String documentId,
                                                   String taskId,
                                                   String originalContent,
                                                   String proposedContent) {
        DiffResult diff = diffService.generateDiff(originalContent, proposedContent);
        PendingDocumentChange pendingChange = new PendingDocumentChange(
                documentId,
                taskId,
                originalContent,
                proposedContent,
                diff.getDiffHtml(),
                LocalDateTime.now()
        );
        pendingChanges.put(documentId, pendingChange);
        return pendingChange;
    }

    public PendingDocumentChange getPendingChange(String documentId) {
        return pendingChanges.get(documentId);
    }

    public PendingDocumentChange discardPendingChange(String documentId) {
        // 每个文档只保留最新一份待确认改动，丢弃时直接移除即可，避免历史候选和当前候选混淆。
        return pendingChanges.remove(documentId);
    }
}
