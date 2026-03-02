package com.agent.editor.service;

import com.agent.editor.agent.AgentExecutor;
import com.agent.editor.agent.AgentFactory;
import com.agent.editor.agent.AgentState;
import com.agent.editor.dto.AgentTaskRequest;
import com.agent.editor.dto.AgentTaskResponse;
import com.agent.editor.dto.DiffResult;
import com.agent.editor.dto.WebSocketMessage;
import com.agent.editor.model.*;
import com.agent.editor.websocket.WebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DocumentService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);
    
    private final Map<String, Document> documents = new ConcurrentHashMap<>();
    private final Map<String, AgentState> agentTasks = new ConcurrentHashMap<>();
    private final Map<String, List<DiffResult>> diffHistory = new ConcurrentHashMap<>();
    
    @Autowired
    private AgentFactory agentFactory;
    
    @Autowired
    private WebSocketService webSocketService;

    public DocumentService() {
        Document sampleDoc = new Document(
            "doc-001",
            "Welcome Document",
            "Welcome to AI Editor!\n\nThis is a sample document.\nYou can edit it using AI-powered agents.\n\nTry asking the agent to:\n- Format this text\n- Add more content\n- Change the style"
        );
        documents.put(sampleDoc.getId(), sampleDoc);
    }

    public Document createDocument(String title, String content) {
        String id = "doc-" + UUID.randomUUID().toString().substring(0, 8);
        Document doc = new Document(id, title, content);
        documents.put(id, doc);
        logger.info("Document created: {}", id);
        return doc;
    }

    public Document getDocument(String id) {
        return documents.get(id);
    }

    public List<Document> getAllDocuments() {
        return new ArrayList<>(documents.values());
    }

    public Document updateDocument(String id, String content) {
        Document doc = documents.get(id);
        if (doc != null) {
            doc.setContent(content);
            doc.setUpdatedAt(LocalDateTime.now());
            logger.info("Document updated: {}", id);
        }
        return doc;
    }

    public boolean deleteDocument(String id) {
        logger.info("Document deleted: {}", id);
        return documents.remove(id) != null;
    }

    public AgentTaskResponse executeAgentTask(AgentTaskRequest request) {
        Document document = getDocument(request.getDocumentId());
        
        if (document == null) {
            throw new IllegalArgumentException("Document not found: " + request.getDocumentId());
        }
        
        AgentMode mode = request.getMode() != null ? request.getMode() : AgentMode.REACT;
        
        AgentExecutor agent = agentFactory.getAgent(mode);
        
        if (agent == null) {
            throw new IllegalArgumentException("Unsupported agent mode: " + mode);
        }
        
        String sessionId = request.getSessionId() != null ? 
            request.getSessionId() : UUID.randomUUID().toString();
        
        logger.info("Starting agent task: mode={}, document={}, instruction={}", 
            mode, document.getId(), request.getInstruction());
        
        AgentState state = agent.execute(
            document, 
            request.getInstruction(), 
            sessionId, 
            mode, 
            request.getMaxSteps()
        );
        
        agentTasks.put(state.getTaskId(), state);
        
        if (state.getDocument().getContent() != null) {
            String originalContent = document.getContent();
            updateDocument(document.getId(), state.getDocument().getContent());
            
            DiffResult diff = generateDiff(document.getId(), originalContent, 
                state.getDocument().getContent());
            
            diffHistory.computeIfAbsent(document.getId(), k -> new ArrayList<>()).add(diff);
        }
        
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

    public DiffResult generateDiff(String documentId, String originalContent, String modifiedContent) {
        String diffHtml = computeSimpleDiff(originalContent, modifiedContent);
        return new DiffResult(originalContent, modifiedContent, diffHtml);
    }

    public List<DiffResult> getDiffHistory(String documentId) {
        return diffHistory.getOrDefault(documentId, Collections.emptyList());
    }

    private String computeSimpleDiff(String original, String modified) {
        if (original == null) original = "";
        if (modified == null) modified = "";
        
        String[] originalLines = original.split("\n");
        String[] modifiedLines = modified.split("\n");
        
        StringBuilder diff = new StringBuilder();
        
        int i = 0, j = 0;
        while (i < originalLines.length || j < modifiedLines.length) {
            if (i >= originalLines.length) {
                diff.append("<div class='diff-add'>+ ").append(escapeHtml(modifiedLines[j])).append("</div>");
                j++;
            } else if (j >= modifiedLines.length) {
                diff.append("<div class='diff-remove'>- ").append(escapeHtml(originalLines[i])).append("</div>");
                i++;
            } else if (originalLines[i].equals(modifiedLines[j])) {
                diff.append("<div class='diff-same'>  ").append(escapeHtml(originalLines[i])).append("</div>");
                i++;
                j++;
            } else {
                diff.append("<div class='diff-remove'>- ").append(escapeHtml(originalLines[i])).append("</div>");
                diff.append("<div class='diff-add'>+ ").append(escapeHtml(modifiedLines[j])).append("</div>");
                i++;
                j++;
            }
        }
        
        return diff.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    private AgentTaskResponse buildTaskResponse(AgentState state) {
        AgentTaskResponse response = new AgentTaskResponse();
        response.setTaskId(state.getTaskId());
        response.setDocumentId(state.getDocument().getId());
        response.setStatus(state.getStatus());
        response.setTotalSteps(state.getCurrentStep());
        
        if (!state.getSteps().isEmpty()) {
            response.setCurrentStep(state.getSteps().get(state.getSteps().size() - 1));
        }
        
        response.setFinalResult(state.getDocument().getContent());
        
        if (state.getStartTime() > 0) {
            response.setStartTime(LocalDateTime.now());
        }
        if (state.getEndTime() > 0) {
            response.setEndTime(LocalDateTime.now());
        }
        
        return response;
    }
}
