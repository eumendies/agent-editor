package com.agent.editor.controller;

import com.agent.editor.agent.event.ExecutionEvent;
import com.agent.editor.dto.AgentTaskRequest;
import com.agent.editor.dto.AgentTaskResponse;
import com.agent.editor.model.AgentMode;
import com.agent.editor.service.TaskApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/agent")
@Tag(name = "AI Agent V2", description = "Native AI Agent v2 operations")
public class AgentV2Controller {

    private final TaskApplicationService taskApplicationService;

    public AgentV2Controller(TaskApplicationService taskApplicationService) {
        this.taskApplicationService = taskApplicationService;
    }

    @PostMapping("/execute")
    @Operation(summary = "Execute agent task via native v2 API")
    public ResponseEntity<AgentTaskResponse> executeAgentTask(@RequestBody AgentTaskRequest request) {
        try {
            return ResponseEntity.accepted().body(taskApplicationService.executeV2(request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    @GetMapping("/task/{taskId}")
    @Operation(summary = "Get task status via native v2 API")
    public ResponseEntity<AgentTaskResponse> getTaskStatus(
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        AgentTaskResponse response = taskApplicationService.getTaskStatus(taskId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/task/{taskId}/events")
    @Operation(summary = "Get native execution events for a task")
    public ResponseEntity<List<ExecutionEvent>> getTaskEvents(
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        return ResponseEntity.ok(taskApplicationService.getTaskEvents(taskId));
    }

    @GetMapping("/modes")
    @Operation(summary = "Get supported agent modes")
    public ResponseEntity<List<String>> getSupportedModes() {
        return ResponseEntity.ok(List.of(AgentMode.values()).stream()
                .map(Enum::name)
                .toList());
    }
}
