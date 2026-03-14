package com.agent.editor.service;

import com.agent.editor.dto.DiffResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DiffService {

    private final Map<String, List<DiffResult>> diffHistory = new ConcurrentHashMap<>();

    public DiffResult recordDiff(String documentId, String originalContent, String modifiedContent) {
        DiffResult result = generateDiff(originalContent, modifiedContent);
        diffHistory.computeIfAbsent(documentId, ignored -> new ArrayList<>()).add(result);
        return result;
    }

    public List<DiffResult> getDiffHistory(String documentId) {
        return diffHistory.getOrDefault(documentId, Collections.emptyList());
    }

    public DiffResult generateDiff(String originalContent, String modifiedContent) {
        return new DiffResult(originalContent, modifiedContent, computeSimpleDiff(originalContent, modifiedContent));
    }

    private String computeSimpleDiff(String original, String modified) {
        String safeOriginal = original == null ? "" : original;
        String safeModified = modified == null ? "" : modified;

        String[] originalLines = safeOriginal.split("\n");
        String[] modifiedLines = safeModified.split("\n");
        StringBuilder diff = new StringBuilder();

        int i = 0;
        int j = 0;
        while (i < originalLines.length || j < modifiedLines.length) {
            if (i >= originalLines.length) {
                diff.append("<div class='diff-add'>+ ").append(escapeHtml(modifiedLines[j])).append("</div>");
                j++;
            } else if (j >= modifiedLines.length) {
                diff.append("<div class='diff-remove'>- ").append(escapeHtml(originalLines[i])).append("</div>");
                i++;
            } else if (originalLines[i].equals(modifiedLines[j])) {
                diff.append("<div class='diff-same'>  ").append(escapeHtml(originalLines[i])).append("</div>");
                i++;
                j++;
            } else {
                diff.append("<div class='diff-remove'>- ").append(escapeHtml(originalLines[i])).append("</div>");
                diff.append("<div class='diff-add'>+ ").append(escapeHtml(modifiedLines[j])).append("</div>");
                i++;
                j++;
            }
        }

        return diff.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
