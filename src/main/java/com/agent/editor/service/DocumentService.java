package com.agent.editor.service;

import com.agent.editor.dto.DiffResult;
import com.agent.editor.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    private static final List<SeedDocumentDefinition> DEFAULT_SEED_DOCUMENTS = List.of(
            new SeedDocumentDefinition("doc-001", "Long Document", "classpath:documents/doc-001.md"),
            new SeedDocumentDefinition("doc-002", "Short Document", "classpath:documents/doc-002.md")
    );

    private final Map<String, Document> documents = new ConcurrentHashMap<>();
    private final Map<String, List<DiffResult>> diffHistory = new ConcurrentHashMap<>();

    public DocumentService() {
        this(new DefaultResourceLoader());
    }

    DocumentService(ResourceLoader resourceLoader) {
        seedDefaultDocuments(resourceLoader);
    }

    private void seedDefaultDocuments(ResourceLoader resourceLoader) {
        for (SeedDocumentDefinition seedDocument : DEFAULT_SEED_DOCUMENTS) {
            documents.put(
                    seedDocument.id,
                    new Document(
                            seedDocument.id,
                            seedDocument.title,
                            readSeededContent(resourceLoader, seedDocument.resourceLocation)
                    )
            );
        }
    }

    private String readSeededContent(ResourceLoader resourceLoader, String resourceLocation) {
        Resource resource = resourceLoader.getResource(resourceLocation);
        // 种子文档属于启动基础数据，读取失败时直接抛错，避免服务带着不完整状态继续运行。
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load seeded document resource: " + resourceLocation, exception);
        }
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
        if (original == null) {
            original = "";
        }
        if (modified == null) {
            modified = "";
        }

        String[] originalLines = original.split("\n");
        String[] modifiedLines = modified.split("\n");

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

    private static final class SeedDocumentDefinition {

        private final String id;
        private final String title;
        private final String resourceLocation;

        private SeedDocumentDefinition(String id, String title, String resourceLocation) {
            this.id = id;
            this.title = title;
            this.resourceLocation = resourceLocation;
        }
    }
}
