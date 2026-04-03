package com.agent.editor.agent.v2.tool.document;

import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.config.DocumentToolModeProperties;
import com.agent.editor.service.StructuredDocumentService;

import java.util.List;

/**
 * 根据文档估算 token 数和访问角色，决定本轮任务可见的文档工具集合。
 */
public class DocumentToolAccessPolicy {

    private static final List<String> FULL_WRITE_TOOLS = List.of(
            DocumentToolNames.EDIT_DOCUMENT,
            DocumentToolNames.APPEND_TO_DOCUMENT,
            DocumentToolNames.GET_DOCUMENT_SNAPSHOT,
            DocumentToolNames.SEARCH_CONTENT
    );
    private static final List<String> INCREMENTAL_WRITE_TOOLS = List.of(
            DocumentToolNames.READ_DOCUMENT_NODE,
            DocumentToolNames.PATCH_DOCUMENT_NODE,
            DocumentToolNames.SEARCH_CONTENT
    );
    private static final List<String> FULL_REVIEW_TOOLS = List.of(
            DocumentToolNames.GET_DOCUMENT_SNAPSHOT,
            DocumentToolNames.SEARCH_CONTENT,
            DocumentToolNames.ANALYZE_DOCUMENT
    );
    private static final List<String> INCREMENTAL_REVIEW_TOOLS = List.of(
            DocumentToolNames.READ_DOCUMENT_NODE,
            DocumentToolNames.SEARCH_CONTENT,
            DocumentToolNames.ANALYZE_DOCUMENT
    );
    private static final List<String> RESEARCH_TOOLS = List.of(
            DocumentToolNames.RETRIEVE_KNOWLEDGE
    );

    private final StructuredDocumentService structuredDocumentService;
    private final DocumentToolModeProperties properties;

    public DocumentToolAccessPolicy(StructuredDocumentService structuredDocumentService,
                                    DocumentToolModeProperties properties) {
        this.structuredDocumentService = structuredDocumentService;
        this.properties = properties;
    }

    /**
     * 根据当前文档估算 token 数判断本轮任务应使用的文档访问模式。
     *
     * @param document 当前任务看到的文档快照
     * @return 小文档返回 FULL，超阈值文档返回 INCREMENTAL
     */
    public DocumentToolMode resolveMode(DocumentSnapshot document) {
        if (document == null) {
            return DocumentToolMode.FULL;
        }
        int estimatedTokens = structuredDocumentService
                .buildSnapshot(document.getTitle(), document.getContent())
                .getEstimatedTokens();
        return estimatedTokens > properties.getLongDocumentThresholdTokens()
                ? DocumentToolMode.INCREMENTAL
                : DocumentToolMode.FULL;
    }

    /**
     * 根据访问角色和文档模式返回本轮允许暴露给模型的工具白名单。
     *
     * @param document 当前任务看到的文档快照
     * @param role 访问角色
     * @return 已按长文档策略裁剪后的工具集合
     */
    public List<String> allowedTools(DocumentSnapshot document, DocumentToolAccessRole role) {
        return allowedTools(resolveMode(document), role);
    }

    /**
     * 根据已判定的文档模式和访问角色返回本轮允许暴露给模型的工具白名单。
     *
     * @param mode 已判定的文档工具模式
     * @param role 访问角色
     * @return 已按长文档策略裁剪后的工具集合
     */
    public List<String> allowedTools(DocumentToolMode mode, DocumentToolAccessRole role) {
        if (role == null) {
            return List.of();
        }
        if (role == DocumentToolAccessRole.RESEARCH) {
            return RESEARCH_TOOLS;
        }
        if (mode == null) {
            mode = DocumentToolMode.FULL;
        }
        if (role == DocumentToolAccessRole.WRITE) {
            return mode == DocumentToolMode.INCREMENTAL ? INCREMENTAL_WRITE_TOOLS : FULL_WRITE_TOOLS;
        }
        if (role == DocumentToolAccessRole.REVIEW) {
            return mode == DocumentToolMode.INCREMENTAL ? INCREMENTAL_REVIEW_TOOLS : FULL_REVIEW_TOOLS;
        }
        return List.of();
    }
}
