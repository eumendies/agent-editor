package com.agent.editor.utils;

import com.agent.editor.model.ParsedKnowledgeDocument;
import com.agent.editor.utils.pdf.PdfKnowledgeExtractor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class KnowledgeDocumentParser {

    // 入口解析器只负责按文件类型分发，PDF 的版面处理交给专门组件。
    private final PdfKnowledgeExtractor pdfKnowledgeExtractor;

    public KnowledgeDocumentParser() {
        this(new PdfKnowledgeExtractor());
    }

    KnowledgeDocumentParser(PdfKnowledgeExtractor pdfKnowledgeExtractor) {
        this.pdfKnowledgeExtractor = pdfKnowledgeExtractor;
    }

    public ParsedKnowledgeDocument parse(MultipartFile file) {
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            if (fileName.endsWith(".md") || fileName.endsWith(".markdown")) {
                return new ParsedKnowledgeDocument(content, "MARKDOWN");
            }
            if (fileName.endsWith(".txt")) {
                return new ParsedKnowledgeDocument(content, "TEXT");
            }
            if (fileName.endsWith(".pdf")) {
                // PDF 不能按 UTF-8 直接解码，必须走带坐标信息的抽取流程。
                return new ParsedKnowledgeDocument(pdfKnowledgeExtractor.extract(file.getBytes()), "PDF");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read knowledge document", e);
        }

        throw new IllegalArgumentException("Unsupported knowledge document format: " + fileName);
    }
}
