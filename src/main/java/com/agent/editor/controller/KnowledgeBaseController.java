package com.agent.editor.controller;

import com.agent.editor.model.KnowledgeDocument;
import com.agent.editor.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/knowledge")
@Tag(name = "Knowledge Base", description = "Knowledge-base upload operations")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping("/documents")
    @Operation(summary = "Upload knowledge document", description = "Upload a personal knowledge document into the in-memory knowledge base")
    public KnowledgeDocument upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam("category") String category) {
        return knowledgeBaseService.upload(file, category);
    }
}
