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
                本文是一份用于验证结构化文档编辑能力的示例文档。
                
                它同时包含标题前导正文、多级标题、列表、引用和代码块，便于直接在默认文档上测试新的节点读取与增量编辑能力。
                
                # 项目概览
                
                这个示例文档模拟一份产品方案草稿，用来验证模型能否先读取目录，再按章节定位和改写内容。
                
                ## 编辑目标
                
                - 验证模型可以先读取结构摘要
                - 验证模型可以按章节读取正文
                - 验证模型可以针对单个章节返回增量 patch
                
                ## 使用说明
                
                > 说明：默认文档应当足够结构化，这样在前端直接打开后就能观察到 AST、章节树和局部编辑的效果。
                
                你可以尝试让模型只重写某个二级标题，或者要求它为某一节补充更多细节。
                
                # 实施细节
                
                本节包含较长的正文，用来观察结构化读取是否仍然只聚焦当前章节，而不是把整篇内容都塞进上下文。为了让这个效果更明显，这一段会稍微写得更长一些，描述如何在任务初始化阶段只提供目录树、如何在需要时按 nodeId 读取正文，以及如何在提交 patch 后由服务端统一重建 Markdown 内容。
                
                ## 数据示例
                
                下面是一段用于说明配置结构的示例：
                
                ```json
                {
                  "mode": "structured-edit",
                  "readTool": "readDocumentNode",
                  "writeTool": "patchDocumentNode"
                }
                ```
                
                ## 后续事项
                
                未来可以继续补充更长的章节或更复杂的 Markdown 语法，例如表格、任务列表和更深层的标题，以便验证极端场景下的表现。
                
                # 结论
                
                这份默认文档的目标不是展示最终内容质量，而是为结构化编辑链路提供一个开箱即用的验证样本。
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
