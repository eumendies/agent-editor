package com.agent.editor.agent.v2.tool.document;

import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import com.agent.editor.model.RetrievedKnowledgeChunk;
import com.agent.editor.service.KnowledgeRetrievalService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetrieveKnowledgeToolTest {

    @Test
    void shouldReturnRetrievedChunksAsJson() {
        KnowledgeRetrievalService retrievalService = mock(KnowledgeRetrievalService.class);
        when(retrievalService.retrieve("Spring", null, null))
                .thenReturn(List.of(new RetrievedKnowledgeChunk(
                        "doc-1",
                        "resume.md",
                        0,
                        "项目经历",
                        "Spring Boot 项目经验",
                        2.0
                )));
        RetrieveKnowledgeTool tool = new RetrieveKnowledgeTool(retrievalService);

        ToolResult result = tool.execute(
                new ToolInvocation("retrieveKnowledge", "{\"query\":\"Spring\"}"),
                new ToolContext("task-1", null)
        );

        assertTrue(result.message().contains("\"fileName\":\"resume.md\""));
        assertTrue(result.message().contains("\"score\":2.0"));
    }
}
