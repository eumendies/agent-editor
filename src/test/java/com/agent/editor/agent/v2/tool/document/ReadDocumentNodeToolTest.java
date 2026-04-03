package com.agent.editor.agent.v2.tool.document;

import com.agent.editor.agent.v2.core.state.DocumentStructureNode;
import com.agent.editor.agent.v2.core.state.DocumentStructureSnapshot;
import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolResult;
import com.agent.editor.service.StructuredDocumentService;
import com.agent.editor.utils.rag.markdown.MarkdownSectionTreeBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadDocumentNodeToolTest {

    private final StructuredDocumentService structuredDocumentService =
            new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 120, 60);

    @Test
    void shouldReturnNodeStructureMetadataWithoutMutatingContent() {
        String markdown = """
                # Intro

                short intro
                """;
        DocumentStructureSnapshot snapshot = structuredDocumentService.buildSnapshot("Title", markdown);
        DocumentStructureNode intro = snapshot.getNodes().get(0);
        ReadDocumentNodeTool tool = new ReadDocumentNodeTool(structuredDocumentService);

        ToolResult result = tool.execute(
                new ToolInvocation(
                        DocumentToolNames.READ_DOCUMENT_NODE,
                        """
                        {"nodeId":"%s","mode":"structure"}
                        """.formatted(intro.getNodeId()).trim()
                ),
                new ToolContext("task-1", markdown)
        );

        assertTrue(result.getMessage().contains("\"mode\":\"structure\""));
        assertTrue(result.getMessage().contains("\"nodeId\":\"" + intro.getNodeId() + "\""));
        assertNull(result.getUpdatedContent());
        assertEquals(DocumentToolNames.READ_DOCUMENT_NODE, tool.specification().name());
    }

    @Test
    void shouldRequireBlockSelectionForOversizedLeafContentRead() {
        String markdown = """
                # Chapter

                %s
                """.formatted(repeatParagraph("leaf paragraph", 10));
        DocumentStructureSnapshot snapshot = structuredDocumentService.buildSnapshot("Title", markdown);
        DocumentStructureNode chapter = snapshot.getNodes().get(0);
        ReadDocumentNodeTool tool = new ReadDocumentNodeTool(structuredDocumentService);

        ToolResult result = tool.execute(
                new ToolInvocation(
                        DocumentToolNames.READ_DOCUMENT_NODE,
                        """
                        {"nodeId":"%s","mode":"content"}
                        """.formatted(chapter.getNodeId()).trim()
                ),
                new ToolContext("task-1", markdown)
        );

        assertTrue(result.getMessage().contains("\"blockSelectionRequired\":true"));
        assertTrue(result.getMessage().contains("\"mode\":\"content\""));
        assertNull(result.getUpdatedContent());
    }

    @Test
    void shouldReturnBlockDirectoryForOversizedLeafBlocksMode() {
        String markdown = """
                # Chapter

                %s
                """.formatted(repeatParagraph("leaf paragraph", 10));
        DocumentStructureSnapshot snapshot = structuredDocumentService.buildSnapshot("Title", markdown);
        DocumentStructureNode chapter = snapshot.getNodes().get(0);
        ReadDocumentNodeTool tool = new ReadDocumentNodeTool(structuredDocumentService);

        ToolResult result = tool.execute(
                new ToolInvocation(
                        DocumentToolNames.READ_DOCUMENT_NODE,
                        """
                        {"nodeId":"%s","mode":"blocks"}
                        """.formatted(chapter.getNodeId()).trim()
                ),
                new ToolContext("task-1", markdown)
        );

        assertTrue(result.getMessage().contains("\"mode\":\"blocks\""));
        assertTrue(result.getMessage().contains("\"blockId\":\"" + chapter.getNodeId() + "-block-0\""));
    }

    @Test
    void shouldIncludeChildSummariesWhenRequested() {
        String markdown = """
                # Intro

                parent body

                ## Detail

                child body
                """;
        DocumentStructureSnapshot snapshot = structuredDocumentService.buildSnapshot("Title", markdown);
        DocumentStructureNode intro = snapshot.getNodes().get(0);
        ReadDocumentNodeTool tool = new ReadDocumentNodeTool(structuredDocumentService);

        ToolResult result = tool.execute(
                new ToolInvocation(
                        DocumentToolNames.READ_DOCUMENT_NODE,
                        """
                        {"nodeId":"%s","mode":"content","includeChildren":true}
                        """.formatted(intro.getNodeId()).trim()
                ),
                new ToolContext("task-1", markdown)
        );

        assertTrue(result.getMessage().contains("\"content\":\"# Intro\\n\\nparent body\""));
        assertTrue(result.getMessage().contains("\"children\""));
        assertTrue(result.getMessage().contains("\"headingText\":\"Detail\""));
    }

    @Test
    void shouldFailWhenArgumentsAreNotValidJson() {
        ReadDocumentNodeTool tool = new ReadDocumentNodeTool(structuredDocumentService);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> tool.execute(
                new ToolInvocation(DocumentToolNames.READ_DOCUMENT_NODE, "{not-json}"),
                new ToolContext("task-1", "# Intro")
        ));

        assertEquals("Failed to parse tool arguments for " + DocumentToolNames.READ_DOCUMENT_NODE, exception.getMessage());
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
