package com.agent.editor.service;

import com.agent.editor.agent.v1.AgentExecutor;
import com.agent.editor.agent.v1.AgentFactory;
import com.agent.editor.agent.v1.AgentState;
import com.agent.editor.dto.AgentTaskRequest;
import com.agent.editor.dto.AgentTaskResponse;
import com.agent.editor.model.AgentMode;
import com.agent.editor.model.AgentStep;
import com.agent.editor.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Legacy v1 agent runtime preserved for fallback/reference purposes.
 * New execution paths must use agent.v2 via TaskApplicationService.
 */
@Deprecated(forRemoval = false)
@Service
public class LegacyAgentService {

    private static final Logger logger = LoggerFactory.getLogger(LegacyAgentService.class);

    private final DocumentService documentService;
    private final AgentFactory agentFactory;
    private final Map<String, AgentState> agentTasks = new ConcurrentHashMap<>();

    public LegacyAgentService(DocumentService documentService, AgentFactory agentFactory) {
        this.documentService = documentService;
        this.agentFactory = agentFactory;
    }

    public AgentTaskResponse executeAgentTask(AgentTaskRequest request) {
        Document document = requireDocument(request.getDocumentId());
        AgentMode mode = resolveMode(request);
        AgentExecutor agent = requireAgent(mode);
        String sessionId = resolveSessionId(request);
        String originalContent = document.getContent();

        logger.info("Starting legacy agent task: mode={}, document={}, instruction={}",
                mode, document.getId(), request.getInstruction());

        AgentState state = agent.execute(
                document,
                request.getInstruction(),
                sessionId,
                mode,
                request.getMaxSteps()
        );

        agentTasks.put(state.getTaskId(), state);
        applyDocumentUpdate(document, originalContent, state);
        return buildTaskResponse(state);
    }

    public AgentTaskResponse startAgentTaskAsync(AgentTaskRequest request) {
        Document document = requireDocument(request.getDocumentId());
        AgentMode mode = resolveMode(request);
        requireAgent(mode);

        String sessionId = resolveSessionId(request);
        AgentState state = new AgentState(document, request.getInstruction(), mode);
        state.setSessionId(sessionId);
        state.setStatus("RUNNING");

        if (request.getMaxSteps() != null) {
            state.setMaxSteps(request.getMaxSteps());
        }

        state.setStartTime(System.currentTimeMillis());
        agentTasks.put(state.getTaskId(), state);

        logger.info("Created legacy async task: taskId={}, mode={}, document={}",
                state.getTaskId(), mode, document.getId());

        executeLegacyTaskAsync(request, document, mode, sessionId, state);
        return buildTaskResponse(state);
    }

    public AgentTaskResponse getTaskStatus(String taskId) {
        AgentState state = agentTasks.get(taskId);
        if (state == null) {
            return null;
        }
        return buildTaskResponse(state);
    }

    public List<AgentStep> getTaskSteps(String taskId) {
        AgentState state = agentTasks.get(taskId);
        if (state == null) {
            return Collections.emptyList();
        }
        return state.getSteps();
    }

    @Async
    protected CompletableFuture<Void> executeLegacyTaskAsync(AgentTaskRequest request,
                                                             Document document,
                                                             AgentMode mode,
                                                             String sessionId,
                                                             AgentState state) {
        return CompletableFuture.runAsync(() -> {
            try {
                String originalContent = document.getContent();
                AgentExecutor agent = requireAgent(mode);
                AgentState resultState = agent.execute(
                        document,
                        request.getInstruction(),
                        sessionId,
                        mode,
                        request.getMaxSteps()
                );

                agentTasks.put(resultState.getTaskId(), resultState);
                applyDocumentUpdate(document, originalContent, resultState);

                logger.info("Legacy async task completed: taskId={}, status={}",
                        resultState.getTaskId(), resultState.getStatus());
            } catch (Exception e) {
                logger.error("Legacy async task failed: taskId={}", state.getTaskId(), e);
                state.setStatus("ERROR");
            }
        });
    }

    private Document requireDocument(String documentId) {
        Document document = documentService.getDocument(documentId);
        if (document == null) {
            throw new IllegalArgumentException("Document not found: " + documentId);
        }
        return document;
    }

    private AgentMode resolveMode(AgentTaskRequest request) {
        return request.getMode() != null ? request.getMode() : AgentMode.REACT;
    }

    private AgentExecutor requireAgent(AgentMode mode) {
        AgentExecutor agent = agentFactory.getAgent(mode);
        if (agent == null) {
            throw new IllegalArgumentException("Unsupported agent mode: " + mode);
        }
        return agent;
    }

    private String resolveSessionId(AgentTaskRequest request) {
        return request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();
    }

    private void applyDocumentUpdate(Document document, String originalContent, AgentState state) {
        if (state.getDocument().getContent() != null) {
            documentService.updateDocument(document.getId(), state.getDocument().getContent());
            documentService.recordDiff(document.getId(), originalContent, state.getDocument().getContent());
        }
    }

    private AgentTaskResponse buildTaskResponse(AgentState state) {
        AgentTaskResponse response = new AgentTaskResponse();
        response.setTaskId(state.getTaskId());
        response.setDocumentId(state.getDocument().getId());
        response.setStatus(state.getStatus());
        response.setTotalSteps(state.getCurrentStep());

        List<AgentStep> steps = new ArrayList<>(state.getSteps());
        if (!steps.isEmpty()) {
            response.setCurrentStep(steps.get(steps.size() - 1));
        }

        response.setFinalResult(state.getDocument().getContent());

        if (state.getStartTime() > 0) {
            response.setStartTime(toLocalDateTime(state.getStartTime()));
        }
        if (state.getEndTime() > 0) {
            response.setEndTime(toLocalDateTime(state.getEndTime()));
        }

        return response;
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }
}
