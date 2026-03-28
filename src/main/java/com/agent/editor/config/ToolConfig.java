package com.agent.editor.config;

import com.agent.editor.agent.v2.tool.ToolRegistry;
import com.agent.editor.agent.v2.tool.document.AnalyzeDocumentTool;
import com.agent.editor.agent.v2.tool.document.AppendToDocumentTool;
import com.agent.editor.agent.v2.tool.document.EditDocumentTool;
import com.agent.editor.agent.v2.tool.document.GetDocumentSnapshotTool;
import com.agent.editor.agent.v2.tool.document.RetrieveKnowledgeTool;
import com.agent.editor.agent.v2.tool.document.SearchContentTool;
import com.agent.editor.service.KnowledgeRetrievalService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

@Configuration
public class ToolConfig {

    @Bean
    public ToolRegistry toolRegistry(ObjectProvider<KnowledgeRetrievalService> retrievalServiceProvider) {
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new EditDocumentTool());
        toolRegistry.register(new AppendToDocumentTool());
        toolRegistry.register(new GetDocumentSnapshotTool());
        toolRegistry.register(new SearchContentTool());
        toolRegistry.register(new AnalyzeDocumentTool());
        retrievalServiceProvider.ifAvailable(service -> toolRegistry.register(new RetrieveKnowledgeTool(service)));
        return toolRegistry;
    }
}
