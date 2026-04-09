package com.agent.editor.agent.tool.document;

import com.agent.editor.agent.core.state.DocumentStructureNode;
import com.agent.editor.agent.core.state.DocumentStructureSnapshot;
import com.agent.editor.agent.core.state.LeafBlockSnapshot;
import com.agent.editor.agent.tool.RecoverableToolException;
import com.agent.editor.agent.tool.ToolContext;
import com.agent.editor.agent.tool.ToolInvocation;
import com.agent.editor.agent.tool.ToolResult;
import com.agent.editor.service.StructuredDocumentService;
import com.agent.editor.utils.rag.markdown.MarkdownSectionTreeBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchDocumentNodeToolTest {

    private final StructuredDocumentService structuredDocumentService =
            new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 120, 60);

    @Test
    void shouldReplaceNormalNodeWithoutEchoingUpdatedDocumentInToolMessage() {
        String markdown = """
                # Intro

                original intro
                """;
        DocumentStructureSnapshot snapshot = structuredDocumentService.buildSnapshot("Title", markdown);
        DocumentStructureNode intro = snapshot.getNodes().get(0);
        String baseHash = structuredDocumentService.readNode("Title", markdown, intro.getNodeId(), "content", null).getBaseHash();
        PatchDocumentNodeTool tool = new PatchDocumentNodeTool(structuredDocumentService);

        ToolResult result = tool.execute(
                new ToolInvocation(
                        DocumentToolNames.PATCH_DOCUMENT_NODE,
                        """
                        {"documentVersion":"%s","nodeId":"%s","baseHash":"%s","operation":"replace_node","content":"# Intro\\n\\nrewritten intro"}
                        """.formatted(snapshot.getDocumentVersion(), intro.getNodeId(), baseHash).trim()
                ),
                new ToolContext("task-1", markdown)
        );

        assertTrue(result.getMessage().contains("\"status\":\"ok\""));
        assertTrue(result.getMessage().contains("\"nodeId\":\"" + intro.getNodeId() + "\""));
        assertTrue(result.getMessage().contains("\"operation\":\"replace_node\""));
        assertTrue(!result.getMessage().contains("rewritten intro"));
        assertTrue(!result.getMessage().contains("\"updatedContent\""));
        assertTrue(result.getUpdatedContent().contains("rewritten intro"));
        assertEquals(DocumentToolNames.PATCH_DOCUMENT_NODE, tool.specification().name());
    }

    @Test
    void shouldReplaceOversizedLeafBlockAndReturnUpdatedDocumentCandidate() {
        String markdown = """
                # Chapter

                %s
                """.formatted(repeatParagraph("leaf paragraph", 10));
        DocumentStructureSnapshot snapshot = structuredDocumentService.buildSnapshot("Title", markdown);
        DocumentStructureNode chapter = snapshot.getNodes().get(0);
        List<LeafBlockSnapshot> blocks = structuredDocumentService.readNode("Title", markdown, chapter.getNodeId(), "blocks", null).getBlocks();
        LeafBlockSnapshot firstBlock = blocks.get(0);
        PatchDocumentNodeTool tool = new PatchDocumentNodeTool(structuredDocumentService);

        ToolResult result = tool.execute(
                new ToolInvocation(
                        DocumentToolNames.PATCH_DOCUMENT_NODE,
                        """
                        {"documentVersion":"%s","nodeId":"%s","blockId":"%s","baseHash":"%s","operation":"replace_block","content":"rewritten leaf block"}
                        """.formatted(snapshot.getDocumentVersion(), chapter.getNodeId(), firstBlock.getBlockId(), firstBlock.getHash()).trim()
                ),
                new ToolContext("task-1", markdown)
        );

        assertTrue(result.getMessage().contains("\"status\":\"ok\""));
        assertTrue(result.getMessage().contains("\"blockId\":\"" + firstBlock.getBlockId() + "\""));
        assertTrue(!result.getMessage().contains("rewritten leaf block"));
        assertTrue(!result.getMessage().contains("\"updatedContent\""));
        assertTrue(result.getUpdatedContent().contains("rewritten leaf block"));
    }

    @Test
    void shouldReturnBaselineMismatchWithoutMutatingContentWhenPatchIsStale() {
        String markdown = """
                # Chapter

                %s
                """.formatted(repeatParagraph("leaf paragraph", 10));
        DocumentStructureSnapshot snapshot = structuredDocumentService.buildSnapshot("Title", markdown);
        DocumentStructureNode chapter = snapshot.getNodes().get(0);
        PatchDocumentNodeTool tool = new PatchDocumentNodeTool(structuredDocumentService);

        ToolResult result = tool.execute(
                new ToolInvocation(
                        DocumentToolNames.PATCH_DOCUMENT_NODE,
                        """
                        {"documentVersion":"%s","nodeId":"%s","blockId":"%s","baseHash":"stale-hash","operation":"replace_block","content":"rewritten leaf block"}
                        """.formatted(snapshot.getDocumentVersion(), chapter.getNodeId(), chapter.getNodeId() + "-block-0").trim()
                ),
                new ToolContext("task-1", markdown)
        );

        assertTrue(result.getMessage().contains("\"status\":\"baseline_mismatch\""));
        assertNull(result.getUpdatedContent());
    }

    @Test
    void shouldThrowRecoverableExceptionWhenPatchNodeIdDoesNotExist() {
        String markdown = """
                # Intro

                original intro
                """;
        DocumentStructureSnapshot snapshot = structuredDocumentService.buildSnapshot("Title", markdown);
        PatchDocumentNodeTool tool = new PatchDocumentNodeTool(structuredDocumentService);

        RecoverableToolException exception = assertThrows(RecoverableToolException.class, () -> tool.execute(
                new ToolInvocation(
                        DocumentToolNames.PATCH_DOCUMENT_NODE,
                        """
                        {"documentVersion":"%s","nodeId":"node-999","baseHash":"hash-1","operation":"replace_node","content":"# Intro\\n\\nrewritten"}
                        """.formatted(snapshot.getDocumentVersion()).trim()
                ),
                new ToolContext("task-1", markdown)
        ));

        assertEquals("Unknown nodeId: node-999", exception.getMessage());
    }

    @Test
    void shouldThrowRecoverableExceptionWhenPatchOperationIsUnsupported() {
        String markdown = """
                # Intro

                original intro
                """;
        DocumentStructureSnapshot snapshot = structuredDocumentService.buildSnapshot("Title", markdown);
        DocumentStructureNode intro = snapshot.getNodes().get(0);
        String baseHash = structuredDocumentService.readNode("Title", markdown, intro.getNodeId(), "content", null).getBaseHash();
        PatchDocumentNodeTool tool = new PatchDocumentNodeTool(structuredDocumentService);

        RecoverableToolException exception = assertThrows(RecoverableToolException.class, () -> tool.execute(
                new ToolInvocation(
                        DocumentToolNames.PATCH_DOCUMENT_NODE,
                        """
                        {"documentVersion":"%s","nodeId":"%s","baseHash":"%s","operation":"invalid_operation","content":"# Intro\\n\\nrewritten intro"}
                        """.formatted(snapshot.getDocumentVersion(), intro.getNodeId(), baseHash).trim()
                ),
                new ToolContext("task-1", markdown)
        ));

        assertEquals("Unsupported patch operation: invalid_operation", exception.getMessage());
    }

    @Test
    void shouldThrowRecoverableExceptionWhenNodeReplacementContentIsInvalid() {
        String markdown = """
                # Intro

                original intro
                """;
        DocumentStructureSnapshot snapshot = structuredDocumentService.buildSnapshot("Title", markdown);
        DocumentStructureNode intro = snapshot.getNodes().get(0);
        String baseHash = structuredDocumentService.readNode("Title", markdown, intro.getNodeId(), "content", null).getBaseHash();
        PatchDocumentNodeTool tool = new PatchDocumentNodeTool(structuredDocumentService);

        RecoverableToolException exception = assertThrows(RecoverableToolException.class, () -> tool.execute(
                new ToolInvocation(
                        DocumentToolNames.PATCH_DOCUMENT_NODE,
                        """
                        {"documentVersion":"%s","nodeId":"%s","baseHash":"%s","operation":"replace_node","content":"invalid replacement without heading"}
                        """.formatted(snapshot.getDocumentVersion(), intro.getNodeId(), baseHash).trim()
                ),
                new ToolContext("task-1", markdown)
        ));

        assertEquals("replace_node content must contain exactly one top-level heading", exception.getMessage());
    }

    @Test
    void shouldFailWhenArgumentsAreNotValidJson() {
        PatchDocumentNodeTool tool = new PatchDocumentNodeTool(structuredDocumentService);

        RecoverableToolException exception = assertThrows(RecoverableToolException.class, () -> tool.execute(
                new ToolInvocation(DocumentToolNames.PATCH_DOCUMENT_NODE, "{not-json}"),
                new ToolContext("task-1", "# Intro")
        ));

        assertEquals("Failed to parse tool arguments for " + DocumentToolNames.PATCH_DOCUMENT_NODE, exception.getMessage());
    }

    private static String repeatParagraph(String base, int count) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                builder.append("\n\n");
            }
            builder.append(base).append(" ").append(index).append(" sentence one. sentence two. sentence three.");
        }
        return builder.toString();
    }
}
