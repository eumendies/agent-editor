package com.agent.editor.service;

import com.agent.editor.agent.v1.AgentExecutor;
import com.agent.editor.agent.v1.AgentFactory;
import com.agent.editor.agent.v1.AgentState;
import com.agent.editor.dto.AgentTaskRequest;
import com.agent.editor.dto.AgentTaskResponse;
import com.agent.editor.model.AgentMode;
import com.agent.editor.model.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyAgentServiceTest {

    @Test
    void shouldExposeLegacyTaskExecutionOutsideDocumentService() {
        DocumentService documentService = new DocumentService();
        AgentFactory agentFactory = mock(AgentFactory.class);
        AgentExecutor agentExecutor = mock(AgentExecutor.class);
        LegacyAgentService service = new LegacyAgentService(documentService, agentFactory);

        AgentTaskRequest request = new AgentTaskRequest();
        request.setDocumentId("doc-001");
        request.setInstruction("rewrite");
        request.setMode(AgentMode.REACT);
        request.setSessionId("session-1");

        Document document = documentService.getDocument("doc-001");
        AgentState resultState = new AgentState(document, "rewrite", AgentMode.REACT);
        resultState.setSessionId("session-1");
        resultState.setStatus("COMPLETED");
        resultState.setCompleted(true);
        resultState.setStartTime(1L);
        resultState.setEndTime(2L);
        resultState.getDocument().setContent("rewritten content");

        when(agentFactory.getAgent(AgentMode.REACT)).thenReturn(agentExecutor);
        when(agentExecutor.execute(eq(document), eq("rewrite"), eq("session-1"), eq(AgentMode.REACT), eq(null)))
                .thenReturn(resultState);

        AgentTaskResponse response = service.executeAgentTask(request);

        assertTrue(LegacyAgentService.class.isAnnotationPresent(Deprecated.class));
        assertNotNull(response.getTaskId());
        assertEquals("COMPLETED", response.getStatus());
        assertEquals("rewritten content", documentService.getDocument("doc-001").getContent());
    }
}
