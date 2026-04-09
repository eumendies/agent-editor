package com.agent.editor.agent.tool.document;

import com.agent.editor.agent.core.state.DocumentSnapshot;
import com.agent.editor.config.DocumentToolModeProperties;
import com.agent.editor.service.StructuredDocumentService;
import com.agent.editor.utils.rag.markdown.MarkdownSectionTreeBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentToolAccessPolicyTest {

    private final StructuredDocumentService structuredDocumentService =
            new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 4_000, 1_200);

    @Test
    void shouldResolveFullModeForSmallDocument() {
        DocumentToolAccessPolicy policy = new DocumentToolAccessPolicy(
                structuredDocumentService,
                new DocumentToolModeProperties(50)
        );

        assertEquals(
                DocumentToolMode.FULL,
                policy.resolveMode(new DocumentSnapshot("doc-1", "Title", "small body"))
        );
    }

    @Test
    void shouldResolveIncrementalModeForLargeDocument() {
        DocumentToolAccessPolicy policy = new DocumentToolAccessPolicy(
                structuredDocumentService,
                new DocumentToolModeProperties(10)
        );

        assertEquals(
                DocumentToolMode.INCREMENTAL,
                policy.resolveMode(new DocumentSnapshot("doc-1", "Title", "x".repeat(80)))
        );
    }

    @Test
    void shouldExposeWholeDocumentToolsForWriteRoleInFullMode() {
        DocumentToolAccessPolicy policy = new DocumentToolAccessPolicy(
                structuredDocumentService,
                new DocumentToolModeProperties(50)
        );

        assertEquals(
                List.of(
                        DocumentToolNames.EDIT_DOCUMENT,
                        DocumentToolNames.APPEND_TO_DOCUMENT,
                        DocumentToolNames.GET_DOCUMENT_SNAPSHOT,
                        DocumentToolNames.SEARCH_CONTENT
                ),
                policy.allowedTools(
                        new DocumentSnapshot("doc-1", "Title", "small body"),
                        DocumentToolAccessRole.WRITE
                )
        );
    }

    @Test
    void shouldExposeIncrementalToolsForWriteRoleInIncrementalMode() {
        DocumentToolAccessPolicy policy = new DocumentToolAccessPolicy(
                structuredDocumentService,
                new DocumentToolModeProperties(10)
        );

        assertEquals(
                List.of(
                        DocumentToolNames.READ_DOCUMENT_NODE,
                        DocumentToolNames.PATCH_DOCUMENT_NODE,
                        DocumentToolNames.SEARCH_CONTENT
                ),
                policy.allowedTools(
                        new DocumentSnapshot("doc-1", "Title", "x".repeat(80)),
                        DocumentToolAccessRole.WRITE
                )
        );
    }

    @Test
    void shouldSwitchReviewRoleBetweenSnapshotAndTargetedRead() {
        DocumentToolAccessPolicy policy = new DocumentToolAccessPolicy(
                structuredDocumentService,
                new DocumentToolModeProperties(10)
        );

        assertEquals(
                List.of(
                        DocumentToolNames.READ_DOCUMENT_NODE,
                        DocumentToolNames.SEARCH_CONTENT,
                        DocumentToolNames.ANALYZE_DOCUMENT
                ),
                policy.allowedTools(
                        new DocumentSnapshot("doc-1", "Title", "x".repeat(80)),
                        DocumentToolAccessRole.REVIEW
                )
        );
    }

    @Test
    void shouldKeepResearchRoleOnRetrievalOnly() {
        DocumentToolAccessPolicy policy = new DocumentToolAccessPolicy(
                structuredDocumentService,
                new DocumentToolModeProperties(10)
        );

        assertEquals(
                List.of(DocumentToolNames.RETRIEVE_KNOWLEDGE),
                policy.allowedTools(
                        new DocumentSnapshot("doc-1", "Title", "x".repeat(80)),
                        DocumentToolAccessRole.RESEARCH
                )
        );
    }
}
