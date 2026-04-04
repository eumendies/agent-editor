package com.agent.editor.service;

import com.agent.editor.agent.v2.memory.LongTermMemoryExtractor;
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
import com.agent.editor.dto.LongTermMemoryCandidateResponse;
import com.agent.editor.dto.PendingDocumentChange;
import com.agent.editor.dto.PendingLongTermMemoryResponse;
import com.agent.editor.agent.v2.core.memory.PendingLongTermMemoryItem;
import com.agent.editor.repository.LongTermMemoryRepository;
import com.agent.editor.model.AgentStep;
import com.agent.editor.model.AgentMode;
import com.agent.editor.model.Document;
import com.agent.editor.websocket.WebSocketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
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
    private final PendingDocumentChangeService pendingDocumentChangeService;
    private final TaskOrchestrator taskOrchestrator;
    private final LongTermMemoryRetrievalService longTermMemoryRetrievalService;
    private final UserProfilePromptAssembler userProfilePromptAssembler;
    private final LongTermMemoryExtractor longTermMemoryExtractor;
    private final PendingLongTermMemoryService pendingLongTermMemoryService;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final WebSocketService webSocketService;
    private final EventPublisher eventPublisher;
    private final TaskExecutor taskExecutor;

    @Autowired
    public TaskApplicationService(DocumentService documentService,
                                  TaskQueryService taskQueryService,
                                  DiffService diffService,
                                  PendingDocumentChangeService pendingDocumentChangeService,
                                  TaskOrchestrator taskOrchestrator,
                                  LongTermMemoryRetrievalService longTermMemoryRetrievalService,
                                  UserProfilePromptAssembler userProfilePromptAssembler,
                                  LongTermMemoryExtractor longTermMemoryExtractor,
                                  PendingLongTermMemoryService pendingLongTermMemoryService,
                                  ObjectProvider<LongTermMemoryRepository> longTermMemoryRepositoryProvider,
                                  WebSocketService webSocketService,
                                  EventPublisher eventPublisher,
                                  @Qualifier("agentTaskExecutor") TaskExecutor taskExecutor) {
        this(documentService,
                taskQueryService,
                diffService,
                pendingDocumentChangeService,
                taskOrchestrator,
                longTermMemoryRetrievalService,
                userProfilePromptAssembler,
                longTermMemoryExtractor,
                pendingLongTermMemoryService,
                longTermMemoryRepositoryProvider.getIfAvailable(),
                webSocketService,
                eventPublisher,
                taskExecutor);
    }

    public TaskApplicationService(DocumentService documentService,
                                  TaskQueryService taskQueryService,
                                  DiffService diffService,
                                  PendingDocumentChangeService pendingDocumentChangeService,
                                  TaskOrchestrator taskOrchestrator,
                                  LongTermMemoryRetrievalService longTermMemoryRetrievalService,
                                  UserProfilePromptAssembler userProfilePromptAssembler,
                                  LongTermMemoryExtractor longTermMemoryExtractor,
                                  PendingLongTermMemoryService pendingLongTermMemoryService,
                                  LongTermMemoryRepository longTermMemoryRepository,
                                  WebSocketService webSocketService,
                                  EventPublisher eventPublisher,
                                  @Qualifier("agentTaskExecutor") TaskExecutor taskExecutor) {
        this.documentService = documentService;
        this.taskQueryService = taskQueryService;
        this.diffService = diffService;
        this.pendingDocumentChangeService = pendingDocumentChangeService;
        this.taskOrchestrator = taskOrchestrator;
        this.longTermMemoryRetrievalService = longTermMemoryRetrievalService;
        this.userProfilePromptAssembler = userProfilePromptAssembler;
        this.longTermMemoryExtractor = longTermMemoryExtractor;
        this.pendingLongTermMemoryService = pendingLongTermMemoryService;
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.webSocketService = webSocketService;
        this.eventPublisher = eventPublisher;
        this.taskExecutor = taskExecutor;
    }

    public TaskApplicationService(DocumentService documentService,
                                  TaskQueryService taskQueryService,
                                  DiffService diffService,
                                  PendingDocumentChangeService pendingDocumentChangeService,
                                  TaskOrchestrator taskOrchestrator,
                                  LongTermMemoryRetrievalService longTermMemoryRetrievalService,
                                  UserProfilePromptAssembler userProfilePromptAssembler,
                                  WebSocketService webSocketService,
                                  EventPublisher eventPublisher,
                                  @Qualifier("agentTaskExecutor") TaskExecutor taskExecutor) {
        this(documentService,
                taskQueryService,
                diffService,
                pendingDocumentChangeService,
                taskOrchestrator,
                longTermMemoryRetrievalService,
                userProfilePromptAssembler,
                null,
                null,
                (LongTermMemoryRepository) null,
                webSocketService,
                eventPublisher,
                taskExecutor);
    }

    public TaskApplicationService(DocumentService documentService,
                                  TaskQueryService taskQueryService,
                                  DiffService diffService,
                                  PendingDocumentChangeService pendingDocumentChangeService,
                                  TaskOrchestrator taskOrchestrator,
                                  WebSocketService webSocketService,
                                  EventPublisher eventPublisher,
                                  @Qualifier("agentTaskExecutor") TaskExecutor taskExecutor) {
        this(documentService,
                taskQueryService,
                diffService,
                pendingDocumentChangeService,
                taskOrchestrator,
                null,
                new UserProfilePromptAssembler(),
                null,
                null,
                (LongTermMemoryRepository) null,
                webSocketService,
                eventPublisher,
                taskExecutor);
    }

    public AgentTaskResponse execute(AgentTaskRequest request) {
        ExecutionContext context = prepareExecutionContext(request, false);
        taskQueryService.save(new TaskState(context.getTaskId(), TaskStatus.RUNNING, null));
        TaskResult result = executeTask(context, request);
        taskQueryService.save(new TaskState(context.getTaskId(), result.getStatus(), result.getFinalContent()));
        return buildCompletedResponse(
                context.getTaskId(),
                context.getDocumentId(),
                result,
                pendingCandidateResponses(context.getTaskId())
        );
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
        TaskRequest taskRequest = new TaskRequest(
                context.getTaskId(),
                context.getSessionId(),
                context.getAgentType(),
                new DocumentSnapshot(context.getDocumentId(), context.getDocumentTitle(), context.getOriginalContent()),
                request.getInstruction(),
                request.getMaxSteps() != null ? request.getMaxSteps() : 10
        );
        taskRequest.setUserProfileGuidance(loadUserProfileGuidance());
        TaskResult result = taskOrchestrator.execute(taskRequest);

        // agent 完成后先落待确认候选稿，只有用户确认应用时才真正改写文档正文。
        if (result.getFinalContent() != null) {
            pendingDocumentChangeService.savePendingChange(
                    context.getDocumentId(),
                    context.getTaskId(),
                    context.getOriginalContent(),
                    result.getFinalContent()
            );
        }
        extractAndStorePendingLongTermMemoryCandidates(context, request, result);
        return result;
    }

    private String loadUserProfileGuidance() {
        if (longTermMemoryRetrievalService == null || userProfilePromptAssembler == null) {
            return "";
        }
        return userProfilePromptAssembler.assemble(longTermMemoryRetrievalService.loadConfirmedProfiles());
    }

    public PendingLongTermMemoryResponse getPendingLongTermMemoryCandidates(String taskId) {
        if (pendingLongTermMemoryService == null) {
            return null;
        }
        List<PendingLongTermMemoryItem> candidates = pendingLongTermMemoryService.getPendingCandidates(taskId);
        if (candidates == null) {
            return null;
        }
        return toPendingResponse(taskId, candidates);
    }

    public PendingLongTermMemoryResponse confirmLongTermMemoryCandidates(String taskId, List<String> candidateIds) {
        if (pendingLongTermMemoryService == null || longTermMemoryRepository == null) {
            throw new IllegalStateException("Long-term memory confirmation is not configured");
        }
        List<PendingLongTermMemoryItem> confirmed = pendingLongTermMemoryService.confirmCandidates(taskId, candidateIds);
        longTermMemoryRepository.saveAll(confirmed.stream()
                .map(item -> (com.agent.editor.agent.v2.core.memory.LongTermMemoryItem) item)
                .toList());
        return toPendingResponse(taskId, confirmed);
    }

    public PendingLongTermMemoryResponse discardLongTermMemoryCandidates(String taskId, List<String> candidateIds) {
        if (pendingLongTermMemoryService == null) {
            throw new IllegalStateException("Long-term memory discard is not configured");
        }
        return toPendingResponse(taskId, pendingLongTermMemoryService.discardCandidates(taskId, candidateIds));
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

    public PendingDocumentChange getPendingDocumentChange(String documentId) {
        return pendingDocumentChangeService.getPendingChange(documentId);
    }

    public PendingDocumentChange applyPendingDocumentChange(String documentId) {
        PendingDocumentChange pendingChange = pendingDocumentChangeService.getPendingChange(documentId);
        if (pendingChange == null) {
            return null;
        }

        // 只有确认动作才能同时推进“正文写回”和“已应用 diff 历史”两个状态，避免候选改动误入正式文档。
        documentService.updateDocument(documentId, pendingChange.getProposedContent());
        diffService.recordDiff(documentId, pendingChange.getOriginalContent(), pendingChange.getProposedContent());
        pendingDocumentChangeService.discardPendingChange(documentId);
        return pendingChange;
    }

    public PendingDocumentChange discardPendingDocumentChange(String documentId) {
        return pendingDocumentChangeService.discardPendingChange(documentId);
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
        return buildCompletedResponse(taskId, documentId, result, List.of());
    }

    private AgentTaskResponse buildCompletedResponse(String taskId,
                                                     String documentId,
                                                     TaskResult result,
                                                     List<LongTermMemoryCandidateResponse> pendingCandidates) {
        AgentTaskResponse response = new AgentTaskResponse();
        response.setTaskId(taskId);
        response.setDocumentId(documentId);
        response.setStatus(result.getStatus().name());
        response.setFinalResult(result.getFinalContent());
        response.setStartTime(LocalDateTime.now());
        response.setEndTime(LocalDateTime.now());
        response.setPendingMemoryCandidates(pendingCandidates);
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

    private void extractAndStorePendingLongTermMemoryCandidates(ExecutionContext context,
                                                                AgentTaskRequest request,
                                                                TaskResult result) {
        if (longTermMemoryExtractor == null || pendingLongTermMemoryService == null) {
            return;
        }
        List<PendingLongTermMemoryItem> candidates = longTermMemoryExtractor.extractCandidates(
                context.getTaskId(),
                context.getSessionId(),
                context.getDocumentId(),
                result.getMemory()
        );
        if (!candidates.isEmpty()) {
            pendingLongTermMemoryService.savePendingCandidates(context.getTaskId(), candidates);
        }
    }

    private List<LongTermMemoryCandidateResponse> pendingCandidateResponses(String taskId) {
        PendingLongTermMemoryResponse response = getPendingLongTermMemoryCandidates(taskId);
        return response == null ? List.of() : response.getCandidates();
    }

    private PendingLongTermMemoryResponse toPendingResponse(String taskId,
                                                            List<PendingLongTermMemoryItem> candidates) {
        PendingLongTermMemoryResponse response = new PendingLongTermMemoryResponse();
        response.setTaskId(taskId);
        response.setCandidates(candidates == null ? List.of() : candidates.stream()
                .map(this::toCandidateResponse)
                .toList());
        return response;
    }

    private LongTermMemoryCandidateResponse toCandidateResponse(PendingLongTermMemoryItem candidate) {
        return new LongTermMemoryCandidateResponse(
                candidate.getCandidateId(),
                candidate.getMemoryType() == null ? null : candidate.getMemoryType().name(),
                candidate.getSummary(),
                candidate.getDocumentId()
        );
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
