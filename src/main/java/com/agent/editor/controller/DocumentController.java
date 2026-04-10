package com.agent.editor.controller;

import com.agent.editor.dto.AgentTaskRequest;
import com.agent.editor.dto.AgentTaskResponse;
import com.agent.editor.dto.DiffResult;
import com.agent.editor.model.Document;
import com.agent.editor.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Document Management", description = "Document CRUD operations")
public class DocumentController {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);
    
    @Autowired
    private DocumentService documentService;

    @PostMapping
    @Operation(summary = "Create a new document", description = "Create a new document with title and content")
    public ResponseEntity<Document> createDocument(
            @Parameter(description = "Document title") @RequestParam String title,
            @Parameter(description = "Document content") @RequestParam String content) {
        logger.info("Creating document: {}", title);
        Document doc = documentService.createDocument(title, content);
        return ResponseEntity.ok(doc);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document by ID", description = "Retrieve a document by its ID")
    public ResponseEntity<Document> getDocument(
            @Parameter(description = "Document ID") @PathVariable String id) {
        Document doc = documentService.getDocument(id);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(doc);
    }

    @GetMapping
    @Operation(summary = "Get all documents", description = "Retrieve all documents")
    public ResponseEntity<List<Document>> getAllDocuments() {
        return ResponseEntity.ok(documentService.getAllDocuments());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update document content", description = "Update the content of an existing document")
    public ResponseEntity<Document> updateDocument(
            @Parameter(description = "Document ID") @PathVariable String id,
            @Parameter(description = "New content") @RequestParam String content) {
        Document doc = documentService.updateDocument(id, content);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(doc);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document", description = "Delete a document by its ID")
    public ResponseEntity<Void> deleteDocument(
            @Parameter(description = "Document ID") @PathVariable String id) {
        boolean deleted = documentService.deleteDocument(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
