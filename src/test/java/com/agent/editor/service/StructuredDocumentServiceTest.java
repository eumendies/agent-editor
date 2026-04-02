package com.agent.editor.service;

import com.agent.editor.agent.v2.core.state.DocumentStructureNode;
import com.agent.editor.agent.v2.core.state.DocumentStructureSnapshot;
import com.agent.editor.agent.v2.core.state.LeafBlockSnapshot;
import com.agent.editor.utils.rag.markdown.MarkdownSectionTreeBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredDocumentServiceTest {

    @Test
    void shouldBuildStructureSnapshotWithStableHeadingPathsAndOverflowFlags() {
        StructuredDocumentService service = new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 120, 60);

        DocumentStructureSnapshot snapshot = service.buildSnapshot("doc-1", "Title", """
                # Intro

                short intro

                ## Detail

                short detail

                # Appendix

                %s
                """.formatted(repeatParagraph("long appendix paragraph", 8)));

        assertEquals("doc-1", snapshot.getDocumentId());
        assertEquals("Title", snapshot.getTitle());
        assertEquals(2, snapshot.getNodes().size());

        DocumentStructureNode intro = snapshot.getNodes().get(0);
        assertEquals("Intro", intro.getHeadingText());
        assertEquals("Intro", intro.getPath());
        assertFalse(intro.isLeaf());
        assertFalse(intro.isOverflow());
        assertEquals(1, intro.getChildren().size());

        DocumentStructureNode detail = intro.getChildren().get(0);
        assertEquals("Intro > Detail", detail.getPath());
        assertTrue(detail.isLeaf());
        assertFalse(detail.isOverflow());

        DocumentStructureNode appendix = snapshot.getNodes().get(1);
        assertTrue(appendix.isLeaf());
        assertTrue(appendix.isOverflow());
        assertTrue(appendix.getNodeId().startsWith("node-"));
    }

    @Test
    void shouldSplitOversizedLeafIntoStableBlocksAndReadSingleBlockContent() {
        StructuredDocumentService service = new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 120, 60);
        String markdown = """
                # Chapter

                %s
                """.formatted(repeatParagraph("leaf paragraph", 10));

        DocumentStructureSnapshot snapshot = service.buildSnapshot("doc-1", "Title", markdown);
        DocumentStructureNode chapter = snapshot.getNodes().get(0);

        StructuredDocumentService.NodeReadResult firstRead = service.readNode(
                "doc-1",
                "Title",
                markdown,
                chapter.getNodeId(),
                "blocks",
                null
        );
        StructuredDocumentService.NodeReadResult secondRead = service.readNode(
                "doc-1",
                "Title",
                markdown,
                chapter.getNodeId(),
                "blocks",
                null
        );

        assertEquals("blocks", firstRead.getMode());
        assertTrue(firstRead.isBlockSelectionRequired());
        assertTrue(firstRead.getBlocks().size() >= 2);
        assertEquals(firstRead.getBlocks().size(), secondRead.getBlocks().size());

        LeafBlockSnapshot firstBlock = firstRead.getBlocks().get(0);
        LeafBlockSnapshot secondBlock = secondRead.getBlocks().get(0);
        assertEquals(firstBlock.getBlockId(), secondBlock.getBlockId());
        assertTrue(firstBlock.getStartOffset() < firstBlock.getEndOffset());
        assertFalse(firstBlock.getHash().isBlank());

        StructuredDocumentService.NodeReadResult blockContent = service.readNode(
                "doc-1",
                "Title",
                markdown,
                chapter.getNodeId(),
                "content",
                firstBlock.getBlockId()
        );

        assertEquals("content", blockContent.getMode());
        assertFalse(blockContent.isBlockSelectionRequired());
        assertTrue(blockContent.getContent().contains("leaf paragraph"));
        assertNotEquals(markdown.trim(), blockContent.getContent().trim());
    }

    @Test
    void shouldSplitSingleOversizedParagraphIntoMultipleBlocks() {
        StructuredDocumentService service = new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 120, 60);
        String markdown = """
                # Chapter

                %s
                """.formatted("single paragraph ".repeat(30).trim());

        DocumentStructureSnapshot snapshot = service.buildSnapshot("doc-1", "Title", markdown);
        DocumentStructureNode chapter = snapshot.getNodes().get(0);
        StructuredDocumentService.NodeReadResult blocks = service.readNode(
                "doc-1",
                "Title",
                markdown,
                chapter.getNodeId(),
                "blocks",
                null
        );

        assertTrue(blocks.getBlocks().size() >= 2);
        assertTrue(blocks.getBlocks().stream().allMatch(block -> block.getCharLength() <= 60));
    }

    @Test
    void shouldExposeLeadingContentAsEditableSyntheticNode() {
        StructuredDocumentService service = new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 120, 60);
        String markdown = """
                preface line one

                preface line two

                # Intro

                intro body
                """;

        DocumentStructureSnapshot snapshot = service.buildSnapshot("doc-1", "Title", markdown);

        assertEquals(2, snapshot.getNodes().size());
        DocumentStructureNode leading = snapshot.getNodes().get(0);
        assertEquals("(leading content)", leading.getHeadingText());
        assertEquals("(leading content)", leading.getPath());
        assertTrue(leading.isLeaf());

        StructuredDocumentService.NodeReadResult read = service.readNode(
                "doc-1",
                "Title",
                markdown,
                leading.getNodeId(),
                "content",
                null
        );
        assertEquals("preface line one\n\npreface line two", read.getContent());

        StructuredDocumentService.PatchResult patch = service.applyPatch(
                "doc-1",
                "Title",
                markdown,
                new StructuredDocumentService.PatchRequest(
                        snapshot.getDocumentVersion(),
                        leading.getNodeId(),
                        null,
                        read.getBaseHash(),
                        "replace_node",
                        "rewritten preface",
                        "tighten preface"
                )
        );

        assertEquals("ok", patch.getStatus());
        assertTrue(patch.getUpdatedContent().startsWith("rewritten preface"));
        assertTrue(patch.getUpdatedContent().contains("# Intro"));
    }

    @Test
    void shouldReadAndPatchParentNodeWithoutInliningChildSections() {
        StructuredDocumentService service = new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 240, 120);
        String markdown = """
                # Intro

                parent body

                ## Detail

                child body
                """;

        DocumentStructureSnapshot snapshot = service.buildSnapshot("doc-1", "Title", markdown);
        DocumentStructureNode intro = snapshot.getNodes().get(0);
        StructuredDocumentService.NodeReadResult read = service.readNode(
                "doc-1",
                "Title",
                markdown,
                intro.getNodeId(),
                "content",
                null
        );

        assertEquals("# Intro\n\nparent body", read.getContent());
        assertFalse(read.getContent().contains("## Detail"));

        StructuredDocumentService.PatchResult patch = service.applyPatch(
                "doc-1",
                "Title",
                markdown,
                new StructuredDocumentService.PatchRequest(
                        snapshot.getDocumentVersion(),
                        intro.getNodeId(),
                        null,
                        read.getBaseHash(),
                        "replace_node",
                        "# Intro\n\nrewritten parent body",
                        "tighten intro"
                )
        );

        assertEquals("ok", patch.getStatus());
        assertTrue(patch.getUpdatedContent().contains("rewritten parent body"));
        assertTrue(patch.getUpdatedContent().contains("## Detail\n\nchild body"));
    }

    @Test
    void shouldApplyNodeAndBlockPatchesWithBaselineValidation() {
        StructuredDocumentService service = new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 120, 60);
        String markdown = """
                # Intro

                original intro

                # Chapter

                %s
                """.formatted(repeatParagraph("leaf paragraph", 10));

        DocumentStructureSnapshot snapshot = service.buildSnapshot("doc-1", "Title", markdown);
        DocumentStructureNode intro = snapshot.getNodes().get(0);
        DocumentStructureNode chapter = snapshot.getNodes().get(1);

        StructuredDocumentService.NodeReadResult introRead = service.readNode(
                "doc-1",
                "Title",
                markdown,
                intro.getNodeId(),
                "content",
                null
        );
        StructuredDocumentService.PatchResult nodePatch = service.applyPatch(
                "doc-1",
                "Title",
                markdown,
                new StructuredDocumentService.PatchRequest(
                        snapshot.getDocumentVersion(),
                        intro.getNodeId(),
                        null,
                        introRead.getBaseHash(),
                        "replace_node",
                        "# Intro\n\nrewritten intro",
                        "rewrite intro"
                )
        );

        assertEquals("ok", nodePatch.getStatus());
        assertTrue(nodePatch.getUpdatedContent().contains("rewritten intro"));

        StructuredDocumentService.NodeReadResult blockDirectory = service.readNode(
                "doc-1",
                "Title",
                nodePatch.getUpdatedContent(),
                chapter.getNodeId(),
                "blocks",
                null
        );
        List<LeafBlockSnapshot> blocks = blockDirectory.getBlocks();
        LeafBlockSnapshot firstBlock = blocks.get(0);
        StructuredDocumentService.PatchResult blockPatch = service.applyPatch(
                "doc-1",
                "Title",
                nodePatch.getUpdatedContent(),
                new StructuredDocumentService.PatchRequest(
                        nodePatch.getDocumentVersion(),
                        chapter.getNodeId(),
                        firstBlock.getBlockId(),
                        firstBlock.getHash(),
                        "replace_block",
                        "rewritten leaf block",
                        "tighten wording"
                )
        );

        assertEquals("ok", blockPatch.getStatus());
        assertTrue(blockPatch.getUpdatedContent().contains("rewritten leaf block"));

        StructuredDocumentService.PatchResult stalePatch = service.applyPatch(
                "doc-1",
                "Title",
                blockPatch.getUpdatedContent(),
                new StructuredDocumentService.PatchRequest(
                        blockPatch.getDocumentVersion(),
                        chapter.getNodeId(),
                        firstBlock.getBlockId(),
                        "stale-hash",
                        "replace_block",
                        "should fail",
                        "stale edit"
                )
        );

        assertEquals("baseline_mismatch", stalePatch.getStatus());
        assertTrue(stalePatch.getUpdatedContent() == null || stalePatch.getUpdatedContent().isBlank());
    }

    @Test
    void shouldRejectPatchWhenDocumentVersionIsStaleEvenIfBaseHashMatches() {
        StructuredDocumentService service = new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 120, 60);
        String markdown = """
                # Intro

                original intro
                """;

        DocumentStructureSnapshot snapshot = service.buildSnapshot("doc-1", "Title", markdown);
        DocumentStructureNode intro = snapshot.getNodes().get(0);
        StructuredDocumentService.NodeReadResult read = service.readNode(
                "doc-1",
                "Title",
                markdown,
                intro.getNodeId(),
                "content",
                null
        );

        StructuredDocumentService.PatchResult patch = service.applyPatch(
                "doc-1",
                "Title",
                markdown,
                new StructuredDocumentService.PatchRequest(
                        "stale-version",
                        intro.getNodeId(),
                        null,
                        read.getBaseHash(),
                        "replace_node",
                        "# Intro\n\nrewritten intro",
                        "rewrite intro"
                )
        );

        assertEquals("baseline_mismatch", patch.getStatus());
    }

    @Test
    void shouldPatchDuplicateSectionsByNodeIdWithoutTouchingEarlierMatch() {
        StructuredDocumentService service = new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 120, 60);
        String markdown = """
                # Repeat

                same body

                # Repeat

                same body
                """;

        DocumentStructureSnapshot snapshot = service.buildSnapshot("doc-1", "Title", markdown);
        DocumentStructureNode second = snapshot.getNodes().get(1);
        StructuredDocumentService.NodeReadResult read = service.readNode(
                "doc-1",
                "Title",
                markdown,
                second.getNodeId(),
                "content",
                null
        );

        StructuredDocumentService.PatchResult patch = service.applyPatch(
                "doc-1",
                "Title",
                markdown,
                new StructuredDocumentService.PatchRequest(
                        snapshot.getDocumentVersion(),
                        second.getNodeId(),
                        null,
                        read.getBaseHash(),
                        "replace_node",
                        "# Repeat\n\nupdated second body",
                        "rewrite second repeat"
                )
        );

        assertEquals("ok", patch.getStatus());
        assertEquals("""
                # Repeat

                same body

                # Repeat

                updated second body
                """.trim(), patch.getUpdatedContent().trim());
    }

    @Test
    void shouldAllowReplacingNodeWithNestedChildSections() {
        StructuredDocumentService service = new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 240, 120);
        String markdown = """
                # Intro

                parent body

                ## Detail

                child body

                # Outro

                outro body
                """;

        DocumentStructureSnapshot snapshot = service.buildSnapshot("doc-1", "Title", markdown);
        DocumentStructureNode intro = snapshot.getNodes().get(0);
        StructuredDocumentService.NodeReadResult read = service.readNode(
                "doc-1",
                "Title",
                markdown,
                intro.getNodeId(),
                "content",
                null
        );

        StructuredDocumentService.PatchResult patch = service.applyPatch(
                "doc-1",
                "Title",
                markdown,
                new StructuredDocumentService.PatchRequest(
                        snapshot.getDocumentVersion(),
                        intro.getNodeId(),
                        null,
                        read.getBaseHash(),
                        "replace_node",
                        """
                        # Intro

                        rewritten parent body

                        ## Detail

                        rewritten child body

                        ### Extra

                        extra child body
                        """.trim(),
                        "rewrite intro subtree"
                )
        );

        assertEquals("ok", patch.getStatus());
        assertTrue(patch.getUpdatedContent().contains("rewritten parent body"));
        assertTrue(patch.getUpdatedContent().contains("rewritten child body"));
        assertTrue(patch.getUpdatedContent().contains("### Extra\n\nextra child body"));
        assertTrue(patch.getUpdatedContent().contains("# Outro\n\noutro body"));
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
