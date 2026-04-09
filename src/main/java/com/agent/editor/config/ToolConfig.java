package com.agent.editor.config;

import com.agent.editor.agent.tool.ToolRegistry;
import com.agent.editor.agent.tool.document.AnalyzeDocumentTool;
import com.agent.editor.agent.tool.document.AppendToDocumentTool;
import com.agent.editor.agent.tool.document.EditDocumentTool;
import com.agent.editor.agent.tool.document.GetDocumentSnapshotTool;
import com.agent.editor.agent.tool.document.PatchDocumentNodeTool;
import com.agent.editor.agent.tool.document.ReadDocumentNodeTool;
import com.agent.editor.agent.tool.document.RetrieveKnowledgeTool;
import com.agent.editor.agent.tool.document.SearchContentTool;
import com.agent.editor.agent.tool.memory.MemorySearchTool;
import com.agent.editor.agent.tool.memory.MemoryUpsertTool;
import com.agent.editor.service.StructuredDocumentService;
import com.agent.editor.service.LongTermMemoryRetrievalService;
import com.agent.editor.service.LongTermMemoryWriteService;
import com.agent.editor.service.KnowledgeRetrievalService;
import com.agent.editor.utils.rag.markdown.MarkdownSectionTreeBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

@Configuration
public class ToolConfig {

    @Bean
    public StructuredDocumentService structuredDocumentService() {
        return new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 4_000, 1_200);
    }

    @Bean
    public ToolRegistry toolRegistry(ObjectProvider<KnowledgeRetrievalService> retrievalServiceProvider,
                                     ObjectProvider<LongTermMemoryRetrievalService> longTermMemoryRetrievalServiceProvider,
                                     ObjectProvider<LongTermMemoryWriteService> longTermMemoryWriteServiceProvider,
                                     StructuredDocumentService structuredDocumentService) {
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new EditDocumentTool());
        toolRegistry.register(new AppendToDocumentTool());
        toolRegistry.register(new GetDocumentSnapshotTool());
        toolRegistry.register(new ReadDocumentNodeTool(structuredDocumentService));
        toolRegistry.register(new PatchDocumentNodeTool(structuredDocumentService));
        toolRegistry.register(new SearchContentTool());
        toolRegistry.register(new AnalyzeDocumentTool());
        retrievalServiceProvider.ifAvailable(service -> toolRegistry.register(new RetrieveKnowledgeTool(service)));
        longTermMemoryRetrievalServiceProvider.ifAvailable(service -> toolRegistry.register(new MemorySearchTool(service)));
        longTermMemoryWriteServiceProvider.ifAvailable(service -> toolRegistry.register(new MemoryUpsertTool(service)));
        return toolRegistry;
    }
}
