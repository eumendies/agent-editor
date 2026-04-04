package com.agent.editor.agent.v2.memory;

import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.ExecutionMemory;
import com.agent.editor.agent.v2.core.memory.LongTermMemoryItem;
import com.agent.editor.agent.v2.core.memory.LongTermMemoryType;
import com.agent.editor.agent.v2.core.memory.PendingLongTermMemoryItem;
import com.agent.editor.service.KnowledgeEmbeddingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LongTermMemoryExtractor {

    private final LongTermMemoryExtractionAiService extractionAiService;
    private final KnowledgeEmbeddingService embeddingService;

    @Autowired
    public LongTermMemoryExtractor(LongTermMemoryExtractionAiService extractionAiService,
                                   KnowledgeEmbeddingService embeddingService) {
        this.extractionAiService = extractionAiService;
        this.embeddingService = embeddingService;
    }

    public List<PendingLongTermMemoryItem> extractCandidates(String taskId,
                                                             String sessionId,
                                                             String documentId,
                                                             ExecutionMemory memory) {
        if (!(memory instanceof ChatTranscriptMemory transcriptMemory) || extractionAiService == null) {
            return List.of();
        }
        String transcript = renderTranscript(transcriptMemory);
        if (transcript.isBlank()) {
            return List.of();
        }
        LongTermMemoryExtractionResponse response = extractionAiService.extract(transcript);
        if (response == null) {
            return List.of();
        }
        return buildCandidates(taskId, sessionId, documentId, response);
    }

    private List<PendingLongTermMemoryItem> buildCandidates(String taskId,
                                                            String sessionId,
                                                            String documentId,
                                                            LongTermMemoryExtractionResponse response) {
        List<PendingLongTermMemoryItem> candidates = new ArrayList<>();
        for (MemoryCandidateSummary summary : response.getUserProfiles()) {
            if (hasText(summary == null ? null : summary.getSummary())) {
                candidates.add(candidate(LongTermMemoryType.USER_PROFILE,
                        "default",
                        null,
                        summary.getSummary(),
                        taskId,
                        sessionId));
            }
        }
        if (hasText(documentId)) {
            for (MemoryCandidateSummary summary : response.getDocumentDecisions()) {
                if (hasText(summary == null ? null : summary.getSummary())) {
                    candidates.add(candidate(LongTermMemoryType.DOCUMENT_DECISION,
                            documentId,
                            documentId,
                            summary.getSummary(),
                            taskId,
                            sessionId));
                }
            }
        }
        return candidates;
    }

    private PendingLongTermMemoryItem candidate(LongTermMemoryType memoryType,
                                                String scopeKey,
                                                String documentId,
                                                String summary,
                                                String taskId,
                                                String sessionId) {
        String memoryId = "memory-" + UUID.randomUUID();
        String candidateId = "candidate-" + UUID.randomUUID();
        LongTermMemoryItem item = new LongTermMemoryItem(
                memoryId,
                memoryType,
                scopeKey,
                documentId,
                summary,
                summary,
                taskId,
                sessionId,
                List.of("candidate"),
                LocalDateTime.now(),
                LocalDateTime.now(),
                embedding(summary)
        );
        return new PendingLongTermMemoryItem(candidateId, item);
    }

    private float[] embedding(String summary) {
        if (embeddingService == null) {
            return new float[]{0.0f};
        }
        return embeddingService.embed(summary);
    }

    private String renderTranscript(ChatTranscriptMemory memory) {
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : memory.getMessages()) {
            String rendered = renderMessage(message);
            if (!rendered.isBlank()) {
                if (builder.length() > 0) {
                    builder.append("\n\n");
                }
                builder.append(rendered);
            }
        }
        return builder.toString();
    }

    private String renderMessage(ChatMessage message) {
        if (message instanceof ChatMessage.SystemChatMessage systemMessage) {
            return "SYSTEM: " + safeText(systemMessage.getText());
        }
        if (message instanceof ChatMessage.UserChatMessage userMessage) {
            return "USER: " + safeText(userMessage.getText());
        }
        if (message instanceof ChatMessage.AiChatMessage aiChatMessage) {
            return "ASSISTANT: " + safeText(aiChatMessage.getText());
        }
        if (message instanceof ChatMessage.AiToolCallChatMessage toolCallChatMessage) {
            return "ASSISTANT_TOOL_CALL: " + safeText(toolCallChatMessage.getText());
        }
        if (message instanceof ChatMessage.ToolExecutionResultChatMessage toolExecutionResultChatMessage) {
            return "TOOL[" + safeText(toolExecutionResultChatMessage.getName()) + "]: "
                    + safeText(toolExecutionResultChatMessage.getText());
        }
        return "";
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
