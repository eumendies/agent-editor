package com.agent.editor.service;

import com.agent.editor.model.ParsedKnowledgeDocument;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class KnowledgeDocumentParser {

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
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read knowledge document", e);
        }

        throw new IllegalArgumentException("Unsupported knowledge document format: " + fileName);
    }
}
