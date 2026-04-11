package com.agent.editor.agent.tool;

import com.agent.editor.agent.core.state.DocumentSnapshot;
import com.agent.editor.agent.tool.external.ExternalToolAccessPolicy;
import com.agent.editor.agent.tool.document.DocumentToolAccessPolicy;
import com.agent.editor.agent.tool.document.DocumentToolAccessRole;
import com.agent.editor.agent.tool.document.DocumentToolMode;
import com.agent.editor.agent.tool.memory.MemoryToolAccessPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * 返回一次执行最终可见的工具白名单。
 * 它负责把 document / memory 等 domain policy 组合成 runtime 真正使用的工具集合。
 */
public class ExecutionToolAccessPolicy {

    private final DocumentToolAccessPolicy documentToolAccessPolicy;
    private final MemoryToolAccessPolicy memoryToolAccessPolicy;
    private final ExternalToolAccessPolicy externalToolAccessPolicy;

    public ExecutionToolAccessPolicy(DocumentToolAccessPolicy documentToolAccessPolicy,
                                     MemoryToolAccessPolicy memoryToolAccessPolicy) {
        this(documentToolAccessPolicy, memoryToolAccessPolicy, new ExternalToolAccessPolicy() {
            @Override
            public List<String> allowedTools(ExecutionToolAccessRole role) {
                return List.of();
            }
        });
    }

    public ExecutionToolAccessPolicy(DocumentToolAccessPolicy documentToolAccessPolicy,
                                     MemoryToolAccessPolicy memoryToolAccessPolicy,
                                     ExternalToolAccessPolicy externalToolAccessPolicy) {
        this.documentToolAccessPolicy = documentToolAccessPolicy;
        this.memoryToolAccessPolicy = memoryToolAccessPolicy;
        this.externalToolAccessPolicy = externalToolAccessPolicy;
    }

    /**
     * 根据当前文档和执行角色返回本轮最终允许暴露给模型的工具集合。
     *
     * @param document 当前任务看到的文档快照
     * @param role 执行阶段角色
     * @return 最终工具白名单
     */
    public List<String> allowedTools(DocumentSnapshot document, ExecutionToolAccessRole role) {
        return allowedTools(resolveMode(document), role);
    }

    /**
     * 根据当前文档估算 token 数，判断本轮 execution 应使用的文档模式。
     *
     * @param document 当前任务看到的文档快照
     * @return 已判定的文档工具模式
     */
    public DocumentToolMode resolveMode(DocumentSnapshot document) {
        return documentToolAccessPolicy.resolveMode(document);
    }

    /**
     * 根据已判定的文档模式和执行角色返回本轮最终允许暴露给模型的工具集合。
     *
     * @param mode 已判定的文档工具模式
     * @param role 执行阶段角色
     * @return 最终工具白名单
     */
    public List<String> allowedTools(DocumentToolMode mode, ExecutionToolAccessRole role) {
        List<String> tools = new ArrayList<>(documentToolAccessPolicy.allowedTools(mode, toDocumentRole(role)));
        // 外部工具权限在组合层统一拼接，避免 document policy 被网络或第三方能力污染。
        for (String tool : externalToolAccessPolicy.allowedTools(role)) {
            if (!tools.contains(tool)) {
                tools.add(tool);
            }
        }
        // 组合层统一做去重和顺序控制，后续新增工具域时 orchestrator 不需要再参与拼接。
        for (String tool : memoryToolAccessPolicy.allowedTools(role)) {
            if (!tools.contains(tool)) {
                tools.add(tool);
            }
        }
        return List.copyOf(tools);
    }

    private DocumentToolAccessRole toDocumentRole(ExecutionToolAccessRole role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case MAIN_WRITE -> DocumentToolAccessRole.WRITE;
            case MEMORY -> null;
            case REVIEW -> DocumentToolAccessRole.REVIEW;
            case RESEARCH -> DocumentToolAccessRole.RESEARCH;
        };
    }
}
