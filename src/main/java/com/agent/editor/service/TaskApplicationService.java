package com.agent.editor.service;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import com.agent.editor.agent.v2.core.state.TaskState;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.agent.v2.task.TaskResult;
import com.agent.editor.dto.AgentTaskRequest;
import com.agent.editor.dto.AgentTaskResponse;
import com.agent.editor.model.AgentStep;
import com.agent.editor.model.AgentMode;
import com.agent.editor.model.Document;
import com.agent.editor.websocket.WebSocketService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
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
    private final EventPublisher eventPublisher;
    private final TaskExecutor taskExecutor;

    public TaskApplicationService(DocumentService documentService,
                                  TaskQueryService taskQueryService,
                                  DiffService diffService,
                                  TaskOrchestrator taskOrchestrator,
                                  WebSocketService webSocketService,
                                  EventPublisher eventPublisher,
                                  @Qualifier("agentTaskExecutor") TaskExecutor taskExecutor) {
        this.documentService = documentService;
        this.taskQueryService = taskQueryService;
        this.diffService = diffService;
        this.taskOrchestrator = taskOrchestrator;
        this.webSocketService = webSocketService;
        this.eventPublisher = eventPublisher;
        this.taskExecutor = taskExecutor;
    }

    public AgentTaskResponse execute(AgentTaskRequest request) {
        ExecutionContext context = prepareExecutionContext(request, false);
        taskQueryService.save(new TaskState(context.getTaskId(), TaskStatus.RUNNING, null));
        TaskResult result = executeTask(context, request);
        taskQueryService.save(new TaskState(context.getTaskId(), result.getStatus(), result.getFinalContent()));
        return buildCompletedResponse(context.getTaskId(), context.getDocumentId(), result);
    }

    public AgentTaskResponse executeV2(AgentTaskRequest request) {
        ExecutionContext context = prepareExecutionContext(request, true);
        taskQueryService.save(new TaskState(context.getTaskId(), TaskStatus.RUNNING, null));
        try {
            // v2 改成“提交即返回”的模型，真正的编排放到专用后台线程里执行，避免继续占住 MVC 请求线程。
            taskExecutor.execute(() -> runAsyncTask(context, request));
        } catch (RuntimeException ex) {
            taskQueryService.remove(context.getTaskId());
            unbindTaskSession(context);
            throw new IllegalStateException("Failed to submit agent task", ex);
        }
        return buildSubmittedResponse(context.getTaskId(), context.getDocumentId());
    }

    private ExecutionContext prepareExecutionContext(AgentTaskRequest request, boolean nativeV2Stream) {
        Document document = documentService.getDocument(request.getDocumentId());
        if (document == null) {
            throw new IllegalArgumentException("Document not found: " + request.getDocumentId());
        }

        String taskId = UUID.randomUUID().toString();
        String sessionId = hasText(request.getSessionId()) ? request.getSessionId() : UUID.randomUUID().toString();
        AgentType agentType = mapAgentType(request.getMode());
        String boundSessionId = null;
        if (hasText(request.getSessionId())) {
            boundSessionId = request.getSessionId();
            // 无论同步还是异步，session 绑定都必须早于任务启动，否则首批执行事件会丢失。
            if (nativeV2Stream) {
                webSocketService.bindV2TaskToSession(request.getSessionId(), taskId);
            } else {
                webSocketService.bindTaskToSession(request.getSessionId(), taskId);
            }
        }
        return new ExecutionContext(
                taskId,
                sessionId,
                agentType,
                document.getId(),
                document.getTitle(),
                document.getContent(),
                boundSessionId,
                nativeV2Stream
        );
    }

    private TaskResult executeTask(ExecutionContext context, AgentTaskRequest request) {
        TaskResult result = taskOrchestrator.execute(new TaskRequest(
                context.getTaskId(),
                context.getSessionId(),
                context.getAgentType(),
                new DocumentSnapshot(context.getDocumentId(), context.getDocumentTitle(), context.getOriginalContent()),
                request.getInstruction(),
                request.getMaxSteps() != null ? request.getMaxSteps() : 10
        ));

        // orchestrator 只返回最终产物，真正的文档持久化和 diff 记录仍然在应用层统一处理。
        if (result.getFinalContent() != null) {
            documentService.updateDocument(context.getDocumentId(), result.getFinalContent());
            diffService.recordDiff(context.getDocumentId(), context.getOriginalContent(), result.getFinalContent());
        }
        return result;
    }

    private void runAsyncTask(ExecutionContext context, AgentTaskRequest request) {
        try {
            TaskResult result = executeTask(context, request);
            // 终态写回必须只在后台任务结束后发生，这样查询接口看到的状态才和真实执行生命周期一致。
            taskQueryService.save(new TaskState(context.getTaskId(), result.getStatus(), result.getFinalContent()));
        } catch (Exception ex) {
            taskQueryService.save(new TaskState(context.getTaskId(), TaskStatus.FAILED, null));
            eventPublisher.publish(new ExecutionEvent(EventType.TASK_FAILED, context.getTaskId(), failureMessage(ex)));
        }
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

    public List<ExecutionEvent> getTaskEvents(String taskId) {
        return taskQueryService.getEvents(taskId);
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

    private AgentTaskResponse buildCompletedResponse(String taskId, String documentId, TaskResult result) {
        AgentTaskResponse response = new AgentTaskResponse();
        response.setTaskId(taskId);
        response.setDocumentId(documentId);
        response.setStatus(result.getStatus().name());
        response.setFinalResult(result.getFinalContent());
        response.setStartTime(LocalDateTime.now());
        response.setEndTime(LocalDateTime.now());
        return response;
    }

    private AgentTaskResponse buildSubmittedResponse(String taskId, String documentId) {
        AgentTaskResponse response = new AgentTaskResponse();
        response.setTaskId(taskId);
        response.setDocumentId(documentId);
        response.setStatus(TaskStatus.RUNNING.name());
        response.setStartTime(LocalDateTime.now());
        response.setEndTime(null);
        return response;
    }

    private String failureMessage(Exception ex) {
        if (hasText(ex.getMessage())) {
            return ex.getMessage();
        }
        return ex.getClass().getSimpleName();
    }

    private void unbindTaskSession(ExecutionContext context) {
        if (!hasText(context.getBoundSessionId())) {
            return;
        }
        if (context.isNativeV2Stream()) {
            webSocketService.unbindV2TaskFromSession(context.getBoundSessionId(), context.getTaskId());
            return;
        }
        webSocketService.unbindTaskFromSession(context.getBoundSessionId(), context.getTaskId());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class ExecutionContext {

        private final String taskId;
        private final String sessionId;
        private final AgentType agentType;
        private final String documentId;
        private final String documentTitle;
        private final String originalContent;
        private final String boundSessionId;
        private final boolean nativeV2Stream;

        private ExecutionContext(String taskId,
                                 String sessionId,
                                 AgentType agentType,
                                 String documentId,
                                 String documentTitle,
                                 String originalContent,
                                 String boundSessionId,
                                 boolean nativeV2Stream) {
            this.taskId = taskId;
            this.sessionId = sessionId;
            this.agentType = agentType;
            this.documentId = documentId;
            this.documentTitle = documentTitle;
            this.originalContent = originalContent;
            this.boundSessionId = boundSessionId;
            this.nativeV2Stream = nativeV2Stream;
        }

        private String getTaskId() {
            return taskId;
        }

        private String getSessionId() {
            return sessionId;
        }

        private AgentType getAgentType() {
            return agentType;
        }

        private String getDocumentId() {
            return documentId;
        }

        private String getDocumentTitle() {
            return documentTitle;
        }

        private String getOriginalContent() {
            return originalContent;
        }

        private String getBoundSessionId() {
            return boundSessionId;
        }

        private boolean isNativeV2Stream() {
            return nativeV2Stream;
        }
    }
}
