package com.agent.editor.service;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.agent.v2.task.TaskResult;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.TaskState;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.dto.AgentTaskRequest;
import com.agent.editor.dto.AgentTaskResponse;
import com.agent.editor.model.AgentStep;
import com.agent.editor.model.AgentMode;
import com.agent.editor.model.Document;
import com.agent.editor.websocket.WebSocketService;
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
    private final WebSocketService webSocketService;

    public TaskApplicationService(DocumentService documentService,
                                  TaskQueryService taskQueryService,
                                  DiffService diffService,
                                  TaskOrchestrator taskOrchestrator,
                                  WebSocketService webSocketService) {
        this.documentService = documentService;
        this.taskQueryService = taskQueryService;
        this.diffService = diffService;
        this.taskOrchestrator = taskOrchestrator;
        this.webSocketService = webSocketService;
    }

    public AgentTaskResponse execute(AgentTaskRequest request) {
        Document document = documentService.getDocument(request.getDocumentId());
        if (document == null) {
            throw new IllegalArgumentException("Document not found: " + request.getDocumentId());
        }

        // 应用层职责是把 HTTP 请求翻译成 v2 任务请求，并在执行前后处理文档与查询态。
        String taskId = UUID.randomUUID().toString();
        String sessionId = hasText(request.getSessionId()) ? request.getSessionId() : UUID.randomUUID().toString();
        AgentType agentType = mapAgentType(request.getMode());

        taskQueryService.save(new TaskState(taskId, TaskStatus.RUNNING, document.getContent()));
        String originalContent = document.getContent();
        if (hasText(request.getSessionId())) {
            // execute 是同步链路，必须在任务启动前完成绑定，否则实时事件会在 HTTP 返回前丢掉。
            webSocketService.bindTaskToSession(request.getSessionId(), taskId);
        }

        TaskResult result = taskOrchestrator.execute(new TaskRequest(
                taskId,
                sessionId,
                agentType,
                new DocumentSnapshot(document.getId(), document.getTitle(), document.getContent()),
                request.getInstruction(),
                request.getMaxSteps() != null ? request.getMaxSteps() : 10
        ));

        // orchestrator 只返回最终产物，真正的文档持久化和 diff 记录仍然在应用层统一处理。
        if (result.getFinalContent() != null) {
            documentService.updateDocument(document.getId(), result.getFinalContent());
            diffService.recordDiff(document.getId(), originalContent, result.getFinalContent());
        }

        taskQueryService.save(new TaskState(taskId, result.getStatus(), result.getFinalContent()));
        return buildResponse(taskId, document.getId(), result);
    }

    public AgentTaskResponse getTaskStatus(String taskId) {
        TaskState state = taskQueryService.findById(taskId);
        if (state == null) {
            return null;
        }

        AgentTaskResponse response = new AgentTaskResponse();
        response.setTaskId(state.getTaskId());
        response.setStatus(state.getStatus().name());
        response.setFinalResult(state.getFinalContent());
        return response;
    }

    public List<AgentStep> getTaskSteps(String taskId) {
        return taskQueryService.getTaskSteps(taskId);
    }

    private AgentType mapAgentType(AgentMode mode) {
        // controller 仍然面对旧的 AgentMode，应用层负责映射到 v2 的统一 agent type。
        if (mode == AgentMode.PLANNING) {
            return AgentType.PLANNING;
        }
        if (mode == AgentMode.SUPERVISOR) {
            return AgentType.SUPERVISOR;
        }
        if (mode == AgentMode.REFLEXION) {
            return AgentType.REFLEXION;
        }
        return AgentType.REACT;
    }

    private AgentTaskResponse buildResponse(String taskId, String documentId, TaskResult result) {
        AgentTaskResponse response = new AgentTaskResponse();
        response.setTaskId(taskId);
        response.setDocumentId(documentId);
        response.setStatus(result.getStatus().name());
        response.setFinalResult(result.getFinalContent());
        response.setStartTime(LocalDateTime.now());
        response.setEndTime(LocalDateTime.now());
        return response;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
