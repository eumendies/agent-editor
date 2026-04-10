package com.agent.editor.agent.tool;

import com.agent.editor.agent.core.state.DocumentSnapshot;
import com.agent.editor.agent.tool.document.DocumentToolAccessPolicy;
import com.agent.editor.agent.tool.document.DocumentToolNames;
import com.agent.editor.agent.tool.memory.MemoryToolAccessPolicy;
import com.agent.editor.agent.tool.memory.MemoryToolNames;
import com.agent.editor.config.DocumentToolModeProperties;
import com.agent.editor.service.StructuredDocumentService;
import com.agent.editor.utils.rag.markdown.MarkdownSectionTreeBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionToolAccessPolicyTest {

    private final StructuredDocumentService structuredDocumentService =
            new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 4_000, 1_200);

    @Test
    void shouldCombineDocumentWriteAndMemoryToolsForMainWriteRole() {
        ExecutionToolAccessPolicy policy = new ExecutionToolAccessPolicy(
                new DocumentToolAccessPolicy(structuredDocumentService, com.agent.editor.testsupport.ConfigurationTestFixtures.documentToolModeProperties(50)),
                new MemoryToolAccessPolicy()
        );

        assertEquals(
                List.of(
                        DocumentToolNames.EDIT_DOCUMENT,
                        DocumentToolNames.APPEND_TO_DOCUMENT,
                        DocumentToolNames.GET_DOCUMENT_SNAPSHOT,
                        DocumentToolNames.SEARCH_CONTENT,
                        MemoryToolNames.SEARCH_MEMORY,
                        MemoryToolNames.UPSERT_MEMORY
                ),
                policy.allowedTools(
                        new DocumentSnapshot("doc-1", "Title", "small body"),
                        ExecutionToolAccessRole.MAIN_WRITE
                )
        );
    }

    @Test
    void shouldExposeMemoryToolsOnlyForMemoryRole() {
        ExecutionToolAccessPolicy policy = new ExecutionToolAccessPolicy(
                new DocumentToolAccessPolicy(structuredDocumentService, com.agent.editor.testsupport.ConfigurationTestFixtures.documentToolModeProperties(50)),
                new MemoryToolAccessPolicy()
        );

        assertEquals(
                List.of(
                        MemoryToolNames.SEARCH_MEMORY,
                        MemoryToolNames.UPSERT_MEMORY
                ),
                policy.allowedTools(
                        new DocumentSnapshot("doc-memory", "Title", "small body"),
                        Enum.valueOf(ExecutionToolAccessRole.class, "MEMORY")
                )
        );
    }

    @Test
    void shouldCombineDocumentReviewAndMemoryReadToolsForReviewRole() {
        ExecutionToolAccessPolicy policy = new ExecutionToolAccessPolicy(
                new DocumentToolAccessPolicy(structuredDocumentService, com.agent.editor.testsupport.ConfigurationTestFixtures.documentToolModeProperties(10)),
                new MemoryToolAccessPolicy()
        );

        assertEquals(
                List.of(
                        DocumentToolNames.READ_DOCUMENT_NODE,
                        DocumentToolNames.SEARCH_CONTENT,
                        DocumentToolNames.ANALYZE_DOCUMENT,
                        MemoryToolNames.SEARCH_MEMORY
                ),
                policy.allowedTools(
                        new DocumentSnapshot("doc-2", "Title", "x".repeat(80)),
                        ExecutionToolAccessRole.REVIEW
                )
        );
    }

    @Test
    void shouldKeepResearchRoleOnRetrievalOnly() {
        ExecutionToolAccessPolicy policy = new ExecutionToolAccessPolicy(
                new DocumentToolAccessPolicy(structuredDocumentService, com.agent.editor.testsupport.ConfigurationTestFixtures.documentToolModeProperties(10)),
                new MemoryToolAccessPolicy()
        );

        assertEquals(
                List.of(DocumentToolNames.RETRIEVE_KNOWLEDGE),
                policy.allowedTools(
                        new DocumentSnapshot("doc-3", "Title", "x".repeat(80)),
                        ExecutionToolAccessRole.RESEARCH
                )
        );
    }
}
