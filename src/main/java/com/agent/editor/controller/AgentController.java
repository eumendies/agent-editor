package com.agent.editor.controller;

import com.agent.editor.dto.AgentTaskRequest;
import com.agent.editor.dto.AgentTaskResponse;
import com.agent.editor.dto.SessionMemoryResponse;
import com.agent.editor.agent.event.ExecutionEvent;
import com.agent.editor.model.AgentMode;
import com.agent.editor.model.AgentStep;
import com.agent.editor.service.SessionMemoryQueryService;
import com.agent.editor.service.TaskApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agent")
@Tag(name = "AI Agent", description = "AI Agent operations for document editing")
public class AgentController {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);

    private final TaskApplicationService taskApplicationService;
    private final SessionMemoryQueryService sessionMemoryQueryService;

    public AgentController(TaskApplicationService taskApplicationService,
                           SessionMemoryQueryService sessionMemoryQueryService) {
        this.taskApplicationService = taskApplicationService;
        this.sessionMemoryQueryService = sessionMemoryQueryService;
    }

    @PostMapping("/execute")
    @Operation(summary = "Execute agent task", description = "Submit an AI agent task for asynchronous native execution")
    public ResponseEntity<AgentTaskResponse> executeAgentTask(@RequestBody AgentTaskRequest request) {
        logger.info("Executing agent task: mode={}, documentId={}, instruction={}",
            request.getMode(), request.getDocumentId(), request.getInstruction());

        try {
            return ResponseEntity.accepted().body(taskApplicationService.executeAsync(request));
        } catch (IllegalArgumentException e) {
            logger.error("Agent task execution failed", e);
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            logger.error("Agent task submission rejected", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (Exception e) {
            logger.error("Unexpected error during agent execution", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/task/{taskId}")
    @Operation(summary = "Get task status", description = "Get the status of an agent task")
    public ResponseEntity<AgentTaskResponse> getTaskStatus(
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        AgentTaskResponse response = taskApplicationService.getTaskStatus(taskId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/task/{taskId}/events")
    @Operation(summary = "Get native execution events", description = "Get all native execution events for an agent task")
    public ResponseEntity<List<ExecutionEvent>> getTaskEvents(
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        return ResponseEntity.ok(taskApplicationService.getTaskEvents(taskId));
    }

    @GetMapping("/task/{taskId}/steps")
    @Operation(summary = "Get task steps", description = "Get all execution steps of an agent task")
    public ResponseEntity<List<AgentStep>> getTaskSteps(
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        List<AgentStep> steps = taskApplicationService.getTaskSteps(taskId);
        return ResponseEntity.ok(steps);
    }

    @GetMapping("/session/{sessionId}/memory")
    @Operation(summary = "Get session memory", description = "Get structured chat memory for a session")
    public ResponseEntity<SessionMemoryResponse> getSessionMemory(
            @Parameter(description = "Session ID") @PathVariable String sessionId) {
        return ResponseEntity.ok(sessionMemoryQueryService.getSessionMemory(sessionId));
    }

    @GetMapping("/modes")
    @Operation(summary = "Get supported agent modes", description = "Get list of supported agent modes")
    public ResponseEntity<List<String>> getSupportedModes() {
        return ResponseEntity.ok(List.of(AgentMode.values()).stream()
                .map(Enum::name)
                .toList());
    }
}
