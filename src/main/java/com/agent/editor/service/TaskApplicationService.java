package com.agent.editor.service;

import com.agent.editor.agent.v2.definition.AgentType;
import com.agent.editor.agent.v2.orchestration.TaskOrchestrator;
import com.agent.editor.agent.v2.orchestration.TaskRequest;
import com.agent.editor.agent.v2.orchestration.TaskResult;
import com.agent.editor.agent.v2.state.DocumentSnapshot;
import com.agent.editor.agent.v2.state.TaskState;
import com.agent.editor.agent.v2.state.TaskStatus;
import com.agent.editor.dto.AgentTaskRequest;
import com.agent.editor.dto.AgentTaskResponse;
import com.agent.editor.model.AgentStep;
import com.agent.editor.model.AgentMode;
import com.agent.editor.model.Document;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TaskApplicationService {

    private final DocumentService documentService;
    private final TaskQueryService taskQueryService;
    private final DiffService diffService;
    private final TaskOrchestrator taskOrchestrator;

    public TaskApplicationService(DocumentService documentService,
                                  TaskQueryService taskQueryService,
                                  DiffService diffService,
                                  TaskOrchestrator taskOrchestrator) {
        this.documentService = documentService;
        this.taskQueryService = taskQueryService;
        this.diffService = diffService;
        this.taskOrchestrator = taskOrchestrator;
    }

    public AgentTaskResponse execute(AgentTaskRequest request) {
        Document document = documentService.getDocument(request.getDocumentId());
        if (document == null) {
            throw new IllegalArgumentException("Document not found: " + request.getDocumentId());
        }

        String taskId = UUID.randomUUID().toString();
        String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();
        AgentType agentType = mapAgentType(request.getMode());

        taskQueryService.save(new TaskState(taskId, TaskStatus.RUNNING, document.getContent()));
        String originalContent = document.getContent();

        TaskResult result = taskOrchestrator.execute(new TaskRequest(
                taskId,
                sessionId,
                agentType,
                new DocumentSnapshot(document.getId(), document.getTitle(), document.getContent()),
                request.getInstruction(),
                request.getMaxSteps() != null ? request.getMaxSteps() : 10
        ));

        if (result.finalContent() != null) {
            documentService.updateDocument(document.getId(), result.finalContent());
            diffService.recordDiff(document.getId(), originalContent, result.finalContent());
        }

        taskQueryService.save(new TaskState(taskId, result.status(), result.finalContent()));
        return buildResponse(taskId, document.getId(), result);
    }

    public AgentTaskResponse getTaskStatus(String taskId) {
        TaskState state = taskQueryService.findById(taskId);
        if (state == null) {
            return null;
        }

        AgentTaskResponse response = new AgentTaskResponse();
        response.setTaskId(state.taskId());
        response.setStatus(state.status().name());
        response.setFinalResult(state.finalContent());
        return response;
    }

    public List<AgentStep> getTaskSteps(String taskId) {
        return taskQueryService.getTaskSteps(taskId);
    }

    private AgentType mapAgentType(AgentMode mode) {
        if (mode == AgentMode.PLANNING) {
            return AgentType.PLANNING;
        }
        return AgentType.REACT;
    }

    private AgentTaskResponse buildResponse(String taskId, String documentId, TaskResult result) {
        AgentTaskResponse response = new AgentTaskResponse();
        response.setTaskId(taskId);
        response.setDocumentId(documentId);
        response.setStatus(result.status().name());
        response.setFinalResult(result.finalContent());
        response.setStartTime(LocalDateTime.now());
        response.setEndTime(LocalDateTime.now());
        return response;
    }
}
