package com.agent.editor.controller;

import com.agent.editor.dto.UserProfileMemoryRequest;
import com.agent.editor.dto.UserProfileMemoryResponse;
import com.agent.editor.service.TaskApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/memory")
@Tag(name = "Long-Term Memory", description = "Long-term memory management operations")
public class LongTermMemoryController {

    private final TaskApplicationService taskApplicationService;

    public LongTermMemoryController(TaskApplicationService taskApplicationService) {
        this.taskApplicationService = taskApplicationService;
    }

    @GetMapping("/profiles")
    @Operation(summary = "List persisted user profile memories")
    public ResponseEntity<List<UserProfileMemoryResponse>> listUserProfiles() {
        return ResponseEntity.ok(taskApplicationService.listUserProfiles());
    }

    @PostMapping("/profiles")
    @Operation(summary = "Create a persisted user profile memory")
    public ResponseEntity<UserProfileMemoryResponse> createUserProfile(@RequestBody UserProfileMemoryRequest request) {
        return ResponseEntity.ok(taskApplicationService.createUserProfile(request));
    }

    @PutMapping("/profiles/{memoryId}")
    @Operation(summary = "Replace a persisted user profile memory")
    public ResponseEntity<UserProfileMemoryResponse> updateUserProfile(
            @Parameter(description = "Memory ID") @PathVariable String memoryId,
            @RequestBody UserProfileMemoryRequest request) {
        return ResponseEntity.ok(taskApplicationService.updateUserProfile(memoryId, request));
    }

    @DeleteMapping("/profiles/{memoryId}")
    @Operation(summary = "Delete a persisted user profile memory")
    public ResponseEntity<Void> deleteUserProfile(
            @Parameter(description = "Memory ID") @PathVariable String memoryId) {
        taskApplicationService.deleteUserProfile(memoryId);
        return ResponseEntity.ok().build();
    }
}
