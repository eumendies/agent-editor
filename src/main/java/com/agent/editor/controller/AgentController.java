package com.agent.editor.controller;

import com.agent.editor.dto.AgentTaskRequest;
import com.agent.editor.dto.AgentTaskResponse;
import com.agent.editor.dto.WebSocketMessage;
import com.agent.editor.model.AgentMode;
import com.agent.editor.model.AgentStep;
import com.agent.editor.service.DocumentService;
import com.agent.editor.websocket.WebSocketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agent")
@Tag(name = "AI Agent", description = "AI Agent operations for document editing")
public class AgentController {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);
    
    @Autowired
    private DocumentService documentService;
    
    @Autowired
    private WebSocketService webSocketService;

    @PostMapping("/execute")
    @Operation(summary = "Execute agent task", description = "Execute an AI agent task to edit a document")
    public ResponseEntity<AgentTaskResponse> executeAgentTask(@RequestBody AgentTaskRequest request) {
        logger.info("Executing agent task: mode={}, documentId={}, instruction={}", 
            request.getMode(), request.getDocumentId(), request.getInstruction());
        
        try {
            AgentTaskResponse response = documentService.executeAgentTask(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Agent task execution failed", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Unexpected error during agent execution", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/task/{taskId}")
    @Operation(summary = "Get task status", description = "Get the status of an agent task")
    public ResponseEntity<AgentTaskResponse> getTaskStatus(
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        AgentTaskResponse response = documentService.getTaskStatus(taskId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/task/{taskId}/steps")
    @Operation(summary = "Get task steps", description = "Get all execution steps of an agent task")
    public ResponseEntity<List<AgentStep>> getTaskSteps(
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        List<AgentStep> steps = documentService.getTaskSteps(taskId);
        return ResponseEntity.ok(steps);
    }

    @GetMapping("/modes")
    @Operation(summary = "Get supported agent modes", description = "Get list of supported agent modes")
    public ResponseEntity<List<String>> getSupportedModes() {
        return ResponseEntity.ok(List.of(
            AgentMode.REACT.name(),
            AgentMode.PLANNING.name()
        ));
    }

    @PostMapping("/connect")
    @Operation(summary = "Create WebSocket session", description = "Create a new WebSocket session for real-time updates")
    public ResponseEntity<WebSocketMessage> createSession() {
        String sessionId = UUID.randomUUID().toString();
        
        WebSocketMessage response = new WebSocketMessage();
        response.setType("SESSION_CREATED");
        response.setSessionId(sessionId);
        response.setContent("WebSocket session created. Connect to /ws/agent?sessionId=" + sessionId);
        
        return ResponseEntity.ok(response);
    }
}
