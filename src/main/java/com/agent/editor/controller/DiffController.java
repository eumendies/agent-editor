package com.agent.editor.controller;

import com.agent.editor.dto.DiffResult;
import com.agent.editor.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/diff")
@Tag(name = "Diff Comparison", description = "Document diff and comparison operations")
public class DiffController {
    
    @Autowired
    private DocumentService documentService;

    @GetMapping("/document/{documentId}")
    @Operation(summary = "Get diff history", description = "Get the diff history for a document")
    public ResponseEntity<List<DiffResult>> getDiffHistory(
            @Parameter(description = "Document ID") @PathVariable String documentId) {
        List<DiffResult> history = documentService.getDiffHistory(documentId);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/compare")
    @Operation(summary = "Compare two texts", description = "Compare two text contents and generate diff")
    public ResponseEntity<DiffResult> compareTexts(
            @Parameter(description = "Original content") @RequestParam String original,
            @Parameter(description = "Modified content") @RequestParam String modified) {
        DiffResult diff = documentService.generateDiff("temp", original, modified);
        return ResponseEntity.ok(diff);
    }
}
