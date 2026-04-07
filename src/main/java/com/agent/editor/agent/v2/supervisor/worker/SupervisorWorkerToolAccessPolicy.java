package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.context.SupervisorContext;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.tool.ExecutionToolAccessPolicy;
import com.agent.editor.agent.v2.tool.ExecutionToolAccessRole;
import com.agent.editor.agent.v2.tool.document.DocumentToolAccessPolicy;
import com.agent.editor.agent.v2.tool.document.DocumentToolMode;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * supervisor worker 侧的工具访问策略。
 * 它负责把 worker 定义映射成 execution role，再统一解析出 document mode 与最终可见工具。
 */
public class SupervisorWorkerToolAccessPolicy {

    private final DocumentToolAccessPolicy documentToolAccessPolicy;
    private final ExecutionToolAccessPolicy executionToolAccessPolicy;

    public SupervisorWorkerToolAccessPolicy(DocumentToolAccessPolicy documentToolAccessPolicy,
                                            ExecutionToolAccessPolicy executionToolAccessPolicy) {
        this.documentToolAccessPolicy = documentToolAccessPolicy;
        this.executionToolAccessPolicy = executionToolAccessPolicy;
    }

    /**
     * 根据 worker 定义与当前文档快照，解析本轮 worker 的文档模式和工具白名单。
     *
     * @param worker 当前即将执行的 worker
     * @param document 当前文档快照
     * @return worker 本轮执行访问结果
     */
    public WorkerToolAccess resolve(SupervisorContext.WorkerDefinition worker, DocumentSnapshot document) {
        ExecutionToolAccessRole executionRole = resolveExecutionRole(worker);
        if (executionRole == null) {
            // 自定义/测试 worker 如果没有接入 execution role，就保留其显式声明的工具，避免编排层再猜测。
            return new WorkerToolAccess(DocumentToolMode.FULL, worker.getAllowedTools());
        }
        DocumentToolMode documentToolMode = resolveDocumentToolMode(executionRole, document);
        return new WorkerToolAccess(
                documentToolMode,
                executionToolAccessPolicy.allowedTools(documentToolMode, executionRole)
        );
    }

    private ExecutionToolAccessRole resolveExecutionRole(SupervisorContext.WorkerDefinition worker) {
        if (worker.getExecutionToolAccessRole() != null) {
            return worker.getExecutionToolAccessRole();
        }
        List<String> capabilities = worker.getCapabilities() == null ? List.of() : worker.getCapabilities();
        if (capabilities.contains("memory")) {
            return ExecutionToolAccessRole.MEMORY;
        }
        if (capabilities.contains("research")) {
            return ExecutionToolAccessRole.RESEARCH;
        }
        if (capabilities.contains("review")) {
            return ExecutionToolAccessRole.REVIEW;
        }
        if (capabilities.contains("write") || capabilities.contains("edit")) {
            return ExecutionToolAccessRole.MAIN_WRITE;
        }
        return null;
    }

    private DocumentToolMode resolveDocumentToolMode(ExecutionToolAccessRole executionRole, DocumentSnapshot document) {
        // research / memory worker 不依赖正文编辑工具，继续固定走 FULL，避免无意义地计算增量文档结构模式。
        if (executionRole == ExecutionToolAccessRole.RESEARCH || executionRole == ExecutionToolAccessRole.MEMORY) {
            return DocumentToolMode.FULL;
        }
        return documentToolAccessPolicy.resolveMode(document);
    }

    @Data
    @AllArgsConstructor
    public static class WorkerToolAccess {

        private DocumentToolMode documentToolMode;
        private List<String> allowedTools;
    }
}
