package com.agent.editor.service;

import com.agent.editor.dto.DiffResult;
import com.agent.editor.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DocumentService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);
    
    private final Map<String, Document> documents = new ConcurrentHashMap<>();
    private final Map<String, List<DiffResult>> diffHistory = new ConcurrentHashMap<>();

    public DocumentService() {
        Document sampleDoc = new Document(
        "doc-001",
        "Welcome Document",
        """
                从前，有一只小狐狸住在森林边缘。它毛色火红，眼睛明亮，对这个世界充满了好奇。
                
                狐狸妈妈总是叮嘱它："孩子，不要在森林里走得太远，外面很危险。"
                
                小狐狸点点头，但心里却想着：森林外面到底是什么样子的呢？
                """
        );
        documents.put(sampleDoc.getId(), sampleDoc);
    }

    public Document createDocument(String title, String content) {
        String id = "doc-" + UUID.randomUUID().toString().substring(0, 8);
        Document doc = new Document(id, title, content);
        documents.put(id, doc);
        logger.info("Document created: {}", id);
        return doc;
    }

    public Document getDocument(String id) {
        return documents.get(id);
    }

    public List<Document> getAllDocuments() {
        return new ArrayList<>(documents.values());
    }

    public Document updateDocument(String id, String content) {
        Document doc = documents.get(id);
        if (doc != null) {
            doc.setContent(content);
            doc.setUpdatedAt(LocalDateTime.now());
            logger.info("Document updated: {}", id);
        }
        return doc;
    }

    public boolean deleteDocument(String id) {
        logger.info("Document deleted: {}", id);
        return documents.remove(id) != null;
    }

    public DiffResult generateDiff(String documentId, String originalContent, String modifiedContent) {
        String diffHtml = computeSimpleDiff(originalContent, modifiedContent);
        return new DiffResult(originalContent, modifiedContent, diffHtml);
    }

    public DiffResult recordDiff(String documentId, String originalContent, String modifiedContent) {
        DiffResult diff = generateDiff(documentId, originalContent, modifiedContent);
        diffHistory.computeIfAbsent(documentId, ignored -> new ArrayList<>()).add(diff);
        return diff;
    }

    public List<DiffResult> getDiffHistory(String documentId) {
        return diffHistory.getOrDefault(documentId, Collections.emptyList());
    }

    private String computeSimpleDiff(String original, String modified) {
        if (original == null) original = "";
        if (modified == null) modified = "";
        
        String[] originalLines = original.split("\n");
        String[] modifiedLines = modified.split("\n");
        
        StringBuilder diff = new StringBuilder();
        
        int i = 0, j = 0;
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
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
