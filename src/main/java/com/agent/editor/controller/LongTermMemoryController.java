package com.agent.editor.controller;

import com.agent.editor.dto.ConfirmLongTermMemoryRequest;
import com.agent.editor.dto.PendingLongTermMemoryResponse;
import com.agent.editor.service.TaskApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/memory")
@Tag(name = "Long-Term Memory", description = "Pending and confirmed long-term memory review operations")
public class LongTermMemoryController {

    private final TaskApplicationService taskApplicationService;

    public LongTermMemoryController(TaskApplicationService taskApplicationService) {
        this.taskApplicationService = taskApplicationService;
    }

    @GetMapping("/task/{taskId}/pending")
    @Operation(summary = "Get pending long-term memory candidates for a task")
    public ResponseEntity<PendingLongTermMemoryResponse> getPendingCandidates(
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        PendingLongTermMemoryResponse response = taskApplicationService.getPendingLongTermMemoryCandidates(taskId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/task/{taskId}/confirm")
    @Operation(summary = "Confirm selected long-term memory candidates")
    public ResponseEntity<PendingLongTermMemoryResponse> confirmCandidates(
            @Parameter(description = "Task ID") @PathVariable String taskId,
            @RequestBody ConfirmLongTermMemoryRequest request) {
        return ResponseEntity.ok(taskApplicationService.confirmLongTermMemoryCandidates(taskId, request.getCandidateIds()));
    }

    @PostMapping("/task/{taskId}/discard")
    @Operation(summary = "Discard selected long-term memory candidates")
    public ResponseEntity<PendingLongTermMemoryResponse> discardCandidates(
            @Parameter(description = "Task ID") @PathVariable String taskId,
            @RequestBody ConfirmLongTermMemoryRequest request) {
        return ResponseEntity.ok(taskApplicationService.discardLongTermMemoryCandidates(taskId, request.getCandidateIds()));
    }
}
