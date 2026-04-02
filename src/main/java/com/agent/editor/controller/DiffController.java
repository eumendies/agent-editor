package com.agent.editor.controller;

import com.agent.editor.dto.DiffResult;
import com.agent.editor.dto.PendingDocumentChange;
import com.agent.editor.service.DiffService;
import com.agent.editor.service.TaskApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/diff")
@Tag(name = "Diff Comparison", description = "Document diff and comparison operations")
public class DiffController {

    private final DiffService diffService;
    private final TaskApplicationService taskApplicationService;

    public DiffController(DiffService diffService, TaskApplicationService taskApplicationService) {
        this.diffService = diffService;
        this.taskApplicationService = taskApplicationService;
    }

    @GetMapping("/document/{documentId}")
    @Operation(summary = "Get diff history", description = "Get the diff history for a document")
    public ResponseEntity<List<DiffResult>> getDiffHistory(
            @Parameter(description = "Document ID") @PathVariable String documentId) {
        List<DiffResult> history = diffService.getDiffHistory(documentId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/document/{documentId}/pending")
    @Operation(summary = "Get pending diff", description = "Get the pending diff awaiting user confirmation")
    public ResponseEntity<PendingDocumentChange> getPendingChange(
            @Parameter(description = "Document ID") @PathVariable String documentId) {
        PendingDocumentChange pendingChange = taskApplicationService.getPendingDocumentChange(documentId);
        if (pendingChange == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(pendingChange);
    }

    @PostMapping("/document/{documentId}/apply")
    @Operation(summary = "Apply pending diff", description = "Apply the pending diff to the document content")
    public ResponseEntity<PendingDocumentChange> applyPendingChange(
            @Parameter(description = "Document ID") @PathVariable String documentId) {
        PendingDocumentChange pendingChange = taskApplicationService.applyPendingDocumentChange(documentId);
        if (pendingChange == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(pendingChange);
    }

    @DeleteMapping("/document/{documentId}/pending")
    @Operation(summary = "Discard pending diff", description = "Discard the pending diff for a document")
    public ResponseEntity<Void> discardPendingChange(
            @Parameter(description = "Document ID") @PathVariable String documentId) {
        taskApplicationService.discardPendingDocumentChange(documentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/compare")
    @Operation(summary = "Compare two texts", description = "Compare two text contents and generate diff")
    public ResponseEntity<DiffResult> compareTexts(
            @Parameter(description = "Original content") @RequestParam String original,
            @Parameter(description = "Modified content") @RequestParam String modified) {
        DiffResult diff = diffService.generateDiff(original, modified);
        return ResponseEntity.ok(diff);
    }
}
