package com.agent.editor.service;

import com.agent.editor.agent.v2.core.state.DocumentStructureNode;
import com.agent.editor.agent.v2.core.state.DocumentStructureSnapshot;
import com.agent.editor.agent.v2.core.state.LeafBlockSnapshot;
import com.agent.editor.utils.rag.markdown.MarkdownSectionDocument;
import com.agent.editor.utils.rag.markdown.MarkdownSectionNode;
import com.agent.editor.utils.rag.markdown.MarkdownSectionTreeBuilder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Data
public class StructuredDocumentService {

    private static final String LEADING_CONTENT_LABEL = "(leading content)";

    private final MarkdownSectionTreeBuilder markdownSectionTreeBuilder;
    private final int overflowThreshold;
    private final int blockSize;

    public StructuredDocumentService(MarkdownSectionTreeBuilder markdownSectionTreeBuilder,
                                     int overflowThreshold,
                                     int blockSize) {
        this.markdownSectionTreeBuilder = markdownSectionTreeBuilder;
        this.overflowThreshold = overflowThreshold;
        this.blockSize = blockSize;
    }

    public DocumentStructureSnapshot buildSnapshot(String documentId, String title, String content) {
        SnapshotState state = buildState(documentId, title, content);
        return state.snapshot();
    }

    public String renderStructureSummary(String documentId, String title, String content) {
        DocumentStructureSnapshot snapshot = buildSnapshot(documentId, title, content);
        if (snapshot.getNodes().isEmpty()) {
            return "(no headings)";
        }
        List<String> lines = new ArrayList<>();
        for (DocumentStructureNode node : snapshot.getNodes()) {
            appendStructureLine(lines, node, 0);
        }
        return String.join("\n", lines);
    }

    public NodeReadResult readNode(String documentId,
                                   String title,
                                   String content,
                                   String nodeId,
                                   String mode,
                                    String blockId) {
        return readNode(documentId, title, content, nodeId, mode, blockId, false);
    }

    public NodeReadResult readNode(String documentId,
                                   String title,
                                   String content,
                                   String nodeId,
                                   String mode,
                                   String blockId,
                                   boolean includeChildren) {
        SnapshotState state = buildState(documentId, title, content);
        IndexedNode indexedNode = state.requireNode(nodeId);
        List<DocumentStructureNode> childSummaries = includeChildren ? indexedNode.childSummaries() : List.of();

        if ("blocks".equals(mode)) {
            return new NodeReadResult(
                    nodeId,
                    "blocks",
                    null,
                    hash(indexedNode.editableText()),
                    true,
                    indexedNode.blocks(blockSize),
                    state.snapshot().getDocumentVersion(),
                    childSummaries
            );
        }
        if (!"content".equals(mode) && !"structure".equals(mode)) {
            throw new IllegalArgumentException("Unsupported read mode: " + mode);
        }
        if ("structure".equals(mode)) {
            return new NodeReadResult(
                    nodeId,
                    "structure",
                    null,
                    hash(indexedNode.editableText()),
                    false,
                    List.of(),
                    state.snapshot().getDocumentVersion(),
                    childSummaries
            );
        }

        if (indexedNode.node.isOverflow() && (blockId == null || blockId.isBlank())) {
            return new NodeReadResult(
                    nodeId,
                    "content",
                    "",
                    hash(indexedNode.editableText()),
                    true,
                    indexedNode.blocks(blockSize),
                    state.snapshot().getDocumentVersion(),
                    childSummaries
            );
        }

        if (blockId != null && !blockId.isBlank()) {
            LeafBlockSnapshot block = indexedNode.requireBlock(blockId, blockSize);
            String bodyText = indexedNode.bodyText();
            return new NodeReadResult(
                    nodeId,
                    "content",
                    bodyText.substring(block.getStartOffset(), block.getEndOffset()),
                    block.getHash(),
                    false,
                    List.of(),
                    state.snapshot().getDocumentVersion(),
                    childSummaries
            );
        }

        return new NodeReadResult(
                nodeId,
                "content",
                indexedNode.editableText(),
                hash(indexedNode.editableText()),
                false,
                List.of(),
                state.snapshot().getDocumentVersion(),
                childSummaries
        );
    }

    public PatchResult applyPatch(String documentId,
                                  String title,
                                  String content,
                                  PatchRequest request) {
        SnapshotState state = buildState(documentId, title, content);
        if (!Objects.equals(request.getDocumentVersion(), state.snapshot().getDocumentVersion())) {
            return PatchResult.baselineMismatch(state.snapshot().getDocumentVersion());
        }
        IndexedNode indexedNode = state.requireNode(request.getNodeId());

        if ("replace_node".equals(request.getOperation())) {
            String currentNodeText = indexedNode.editableText();
            if (!Objects.equals(hash(currentNodeText), request.getBaseHash())) {
                return PatchResult.baselineMismatch(state.snapshot().getDocumentVersion());
            }
            indexedNode.replaceNode(request.getContent());
            String updated = state.document().render();
            return PatchResult.ok(hash(updated), updated);
        }
        if ("replace_block".equals(request.getOperation())) {
            LeafBlockSnapshot block = indexedNode.requireBlock(request.getBlockId(), blockSize);
            if (!Objects.equals(block.getHash(), request.getBaseHash())) {
                return PatchResult.baselineMismatch(state.snapshot().getDocumentVersion());
            }
            indexedNode.replaceBlock(block, request.getContent());
            String updated = state.document().render();
            return PatchResult.ok(hash(updated), updated);
        }
        throw new IllegalArgumentException("Unsupported patch operation: " + request.getOperation());
    }

    private SnapshotState buildState(String documentId, String title, String content) {
        String safeContent = content == null ? "" : content;
        MarkdownSectionDocument document = markdownSectionTreeBuilder.build(safeContent);
        List<IndexedNode> indexedNodes = new ArrayList<>();
        List<DocumentStructureNode> topLevel = new ArrayList<>();
        Counter counter = new Counter();
        if (!document.getLeadingContent().isBlank()) {
            topLevel.add(indexLeadingContent(document, indexedNodes, counter));
        }
        for (MarkdownSectionNode section : document.getSections()) {
            topLevel.add(indexNode(document, section, List.of(), indexedNodes, counter));
        }
        int oversizedNodeCount = (int) indexedNodes.stream().filter(node -> node.node.isOverflow()).count();
        int estimatedTokens = estimateTokens(safeContent);
        DocumentStructureSnapshot snapshot = new DocumentStructureSnapshot(
                documentId,
                hash(safeContent),
                title,
                topLevel,
                estimatedTokens,
                oversizedNodeCount
        );
        return new SnapshotState(document, snapshot, indexedNodes);
    }

    private DocumentStructureNode indexLeadingContent(MarkdownSectionDocument document,
                                                      List<IndexedNode> indexedNodes,
                                                      Counter counter) {
        String nodeId = "node-" + counter.next();
        String text = document.getLeadingContent();
        DocumentStructureNode node = new DocumentStructureNode(
                nodeId,
                LEADING_CONTENT_LABEL,
                LEADING_CONTENT_LABEL,
                "",
                0,
                0,
                text.length(),
                estimateTokens(text),
                true,
                text.length() > overflowThreshold,
                List.of()
        );
        indexedNodes.add(new IndexedNode(document, node, null, true));
        return node;
    }

    private DocumentStructureNode indexNode(MarkdownSectionDocument document,
                                            MarkdownSectionNode section,
                                            List<String> parentPath,
                                            List<IndexedNode> indexedNodes,
                                            Counter counter) {
        List<String> currentPath = new ArrayList<>(parentPath);
        currentPath.add(section.getHeadingText());
        String path = String.join(" > ", currentPath);
        List<DocumentStructureNode> children = new ArrayList<>();
        String nodeId = "node-" + counter.next();
        for (MarkdownSectionNode child : section.getChildren()) {
            children.add(indexNode(document, child, currentPath, indexedNodes, counter));
        }
        String editableText = editableText(section);
        DocumentStructureNode node = new DocumentStructureNode(
                nodeId,
                path,
                section.getHeadingText(),
                section.getHeadingLine(),
                section.getHeadingLevel(),
                children.size(),
                editableText.length(),
                estimateTokens(editableText),
                children.isEmpty(),
                editableText.length() > overflowThreshold,
                children
        );
        indexedNodes.add(new IndexedNode(document, node, section, false));
        return node;
    }

    private String editableText(MarkdownSectionNode section) {
        return section == null ? "" : section.introText();
    }

    private MarkdownSectionNode parseNodeReplacement(String replacement) {
        MarkdownSectionDocument replacementDocument = markdownSectionTreeBuilder.build(replacement == null ? "" : replacement);
        if (!replacementDocument.getLeadingContent().isBlank() || replacementDocument.getSections().size() != 1) {
            throw new IllegalArgumentException("replace_node content must contain exactly one top-level heading");
        }
        return replacementDocument.getSections().get(0);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private String hash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private final class IndexedNode {

        private final MarkdownSectionDocument document;
        private final DocumentStructureNode node;
        private final MarkdownSectionNode source;
        private final boolean leadingContentNode;

        private IndexedNode(MarkdownSectionDocument document,
                            DocumentStructureNode node,
                            MarkdownSectionNode source,
                            boolean leadingContentNode) {
            this.document = document;
            this.node = node;
            this.source = source;
            this.leadingContentNode = leadingContentNode;
        }

        private String editableText() {
            if (leadingContentNode) {
                return document.getLeadingContent();
            }
            return StructuredDocumentService.this.editableText(source);
        }

        private String bodyText() {
            if (leadingContentNode) {
                return document.getLeadingContent();
            }
            return source.getBodyText() == null ? "" : source.getBodyText();
        }

        private List<DocumentStructureNode> childSummaries() {
            return node.getChildren();
        }

        private List<LeafBlockSnapshot> blocks(int currentBlockSize) {
            List<TextSegment> segments = splitSegments(bodyText(), currentBlockSize);
            if (segments.isEmpty()) {
                return List.of(new LeafBlockSnapshot(
                        node.getNodeId(),
                        blockId(node.getNodeId(), 0, ""),
                        0,
                        0,
                        0,
                        0,
                        0,
                        hash(""),
                        ""
                ));
            }

            List<LeafBlockSnapshot> blocks = new ArrayList<>();
            int ordinal = 0;
            for (TextSegment segment : segments) {
                blocks.add(buildBlock(ordinal++, segment.startOffset(), segment.text()));
            }
            return blocks.stream()
                    .sorted(Comparator.comparingInt(LeafBlockSnapshot::getOrdinal))
                    .toList();
        }

        // 块级编辑只改“当前节点直属正文”，避免父节点 patch 时把子章节一起吞掉。
        private LeafBlockSnapshot buildBlock(int ordinal, int startOffset, String blockText) {
            return new LeafBlockSnapshot(
                    node.getNodeId(),
                    blockId(node.getNodeId(), ordinal, blockText),
                    ordinal,
                    startOffset,
                    startOffset + blockText.length(),
                    blockText.length(),
                    estimateTokens(blockText),
                    hash(blockText),
                    summarize(blockText)
            );
        }

        private LeafBlockSnapshot requireBlock(String blockId, int currentBlockSize) {
            return blocks(currentBlockSize).stream()
                    .filter(block -> block.getBlockId().equals(blockId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown blockId: " + blockId));
        }

        private void replaceNode(String replacement) {
            if (leadingContentNode) {
                document.setLeadingContent(replacement == null ? "" : replacement.trim());
                return;
            }
            // replace_node 现在允许直接替换整棵子树，避免模型改父章节时被工具契约硬拦。
            MarkdownSectionNode replacementNode = parseNodeReplacement(replacement);
            source.setHeadingText(replacementNode.getHeadingText());
            source.setHeadingLevel(replacementNode.getHeadingLevel());
            source.setHeadingLine(replacementNode.getHeadingLine());
            source.setBodyText(replacementNode.getBodyText());
            if (!replacementNode.getChildren().isEmpty()) {
                source.setChildren(replacementNode.getChildren());
            }
        }

        private void replaceBlock(LeafBlockSnapshot block, String replacement) {
            String updatedBody = replaceRange(bodyText(), block.getStartOffset(), block.getEndOffset(), replacement);
            if (leadingContentNode) {
                document.setLeadingContent(updatedBody.trim());
                return;
            }
            source.setBodyText(updatedBody.trim());
        }
    }

    private String replaceRange(String original, int start, int end, String replacement) {
        return original.substring(0, start) + replacement + original.substring(end);
    }

    private String summarize(String blockText) {
        if (blockText == null || blockText.isBlank()) {
            return "";
        }
        return blockText.length() <= 40 ? blockText : blockText.substring(0, 40);
    }

    private List<TextSegment> splitSegments(String text, int currentBlockSize) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<TextSegment> segments = new ArrayList<>();
        int cursor = 0;
        while (cursor < text.length()) {
            int paragraphEnd = text.indexOf("\n\n", cursor);
            if (paragraphEnd < 0) {
                paragraphEnd = text.length();
            }
            String paragraph = text.substring(cursor, paragraphEnd);
            if (paragraph.length() <= currentBlockSize) {
                segments.add(new TextSegment(cursor, paragraph));
            } else {
                segments.addAll(splitLongSegment(paragraph, cursor, currentBlockSize));
            }
            cursor = paragraphEnd + 2;
        }
        return segments;
    }

    private List<TextSegment> splitLongSegment(String text, int absoluteStartOffset, int currentBlockSize) {
        List<TextSegment> segments = new ArrayList<>();
        int cursor = 0;
        while (cursor < text.length()) {
            int maxEnd = Math.min(text.length(), cursor + currentBlockSize);
            int splitEnd = findSplitBoundary(text, cursor, maxEnd);
            if (splitEnd <= cursor) {
                splitEnd = maxEnd;
            }
            segments.add(new TextSegment(absoluteStartOffset + cursor, text.substring(cursor, splitEnd)));
            cursor = splitEnd;
            while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
                cursor += 1;
            }
        }
        return segments;
    }

    // 先尝试按句子边界切；实在没有合适边界，再退化到固定窗口，确保超长单段也能被兜住。
    private int findSplitBoundary(String text, int start, int maxEnd) {
        for (int index = maxEnd; index > start; index--) {
            char current = text.charAt(index - 1);
            if (current == '.' || current == '!' || current == '?' || current == '。' || current == '！' || current == '？') {
                return index;
            }
        }
        for (int index = maxEnd; index > start; index--) {
            if (Character.isWhitespace(text.charAt(index - 1))) {
                return index;
            }
        }
        return maxEnd;
    }

    private String blockId(String nodeId, int ordinal, String blockText) {
        return nodeId + "-block-" + ordinal;
    }

    private void appendStructureLine(List<String> lines, DocumentStructureNode node, int depth) {
        String prefix = "  ".repeat(Math.max(0, depth));
        String overflowSuffix = node.isOverflow() ? " [overflow]" : "";
        lines.add(prefix + "- " + node.getHeadingText() + overflowSuffix);
        for (DocumentStructureNode child : node.getChildren()) {
            appendStructureLine(lines, child, depth + 1);
        }
    }

    private static final class Counter {
        private int value;

        private int next() {
            value += 1;
            return value;
        }
    }

    private record SnapshotState(MarkdownSectionDocument document,
                                 DocumentStructureSnapshot snapshot,
                                 List<IndexedNode> indexedNodes) {
        private IndexedNode requireNode(String nodeId) {
            return indexedNodes.stream()
                    .filter(node -> node.node.getNodeId().equals(nodeId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown nodeId: " + nodeId));
        }
    }

    @Data
    @NoArgsConstructor
    public static class NodeReadResult {

        private String nodeId;
        private String mode;
        private String content;
        private String baseHash;
        private boolean blockSelectionRequired;
        private List<LeafBlockSnapshot> blocks = List.of();
        private String documentVersion;
        private List<DocumentStructureNode> children = List.of();

        public NodeReadResult(String nodeId,
                              String mode,
                              String content,
                              String baseHash,
                              boolean blockSelectionRequired,
                              List<LeafBlockSnapshot> blocks,
                              String documentVersion,
                              List<DocumentStructureNode> children) {
            this.nodeId = nodeId;
            this.mode = mode;
            this.content = content;
            this.baseHash = baseHash;
            this.blockSelectionRequired = blockSelectionRequired;
            setBlocks(blocks);
            this.documentVersion = documentVersion;
            setChildren(children);
        }

        public void setBlocks(List<LeafBlockSnapshot> blocks) {
            this.blocks = blocks == null ? List.of() : List.copyOf(blocks);
        }

        public void setChildren(List<DocumentStructureNode> children) {
            this.children = children == null ? List.of() : List.copyOf(children);
        }
    }

    @Data
    @NoArgsConstructor
    public static class PatchRequest {

        private String documentVersion;
        private String nodeId;
        private String blockId;
        private String baseHash;
        private String operation;
        private String content;
        private String reason;

        public PatchRequest(String documentVersion,
                            String nodeId,
                            String blockId,
                            String baseHash,
                            String operation,
                            String content,
                            String reason) {
            this.documentVersion = documentVersion;
            this.nodeId = nodeId;
            this.blockId = blockId;
            this.baseHash = baseHash;
            this.operation = operation;
            this.content = content;
            this.reason = reason;
        }
    }

    @Data
    @NoArgsConstructor
    public static class PatchResult {

        private String status;
        private String documentVersion;
        private String updatedContent;

        private PatchResult(String status, String documentVersion, String updatedContent) {
            this.status = status;
            this.documentVersion = documentVersion;
            this.updatedContent = updatedContent;
        }

        private static PatchResult ok(String documentVersion, String updatedContent) {
            return new PatchResult("ok", documentVersion, updatedContent);
        }

        private static PatchResult baselineMismatch(String documentVersion) {
            return new PatchResult("baseline_mismatch", documentVersion, null);
        }
    }

    private record TextSegment(int startOffset, String text) {
    }
}
