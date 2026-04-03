package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.tool.document.DocumentToolMode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * runtime 执行入口请求，描述一次 agent 运行所需的任务、文档和约束。
 */
@Data
@NoArgsConstructor
public class ExecutionRequest {

    // 任务唯一标识。
    private String taskId;
    // 会话标识，用于隔离跨轮记忆。
    private String sessionId;
    // 本次请求指定的 agent 类型。
    private AgentType agentType;
    // 当前要处理的文档快照。
    private DocumentSnapshot document;
    // 用户或上游下发的执行指令。
    private String instruction;
    // 最大允许迭代轮数。
    private int maxIterations;
    // supervisor 为子 worker 指定的执行者 ID。
    private String workerId;
    // 当前任务选定的文档工具模式，供 prompt 与工具策略共享同一判断结果。
    private DocumentToolMode documentToolMode = DocumentToolMode.FULL;
    // 本次执行允许暴露给模型的工具白名单。
    private List<String> allowedTools = List.of();

    public ExecutionRequest(String taskId,
                            String sessionId,
                            AgentType agentType,
                            DocumentSnapshot document,
                            String instruction,
                            int maxIterations,
                            String workerId,
                            List<String> allowedTools) {
        this.taskId = taskId;
        this.sessionId = sessionId;
        this.agentType = agentType;
        this.document = document;
        this.instruction = instruction;
        this.maxIterations = maxIterations;
        this.workerId = workerId;
        setAllowedTools(allowedTools);
    }

    public ExecutionRequest(String taskId,
                            String sessionId,
                            AgentType agentType,
                            DocumentSnapshot document,
                            String instruction,
                            int maxIterations,
                            List<String> allowedTools) {
        this(taskId, sessionId, agentType, document, instruction, maxIterations, null, allowedTools);
    }

    public ExecutionRequest(String taskId,
                            String sessionId,
                            AgentType agentType,
                            DocumentSnapshot document,
                            String instruction,
                            int maxIterations) {
        this(taskId, sessionId, agentType, document, instruction, maxIterations, null, List.of());
    }

    public void setAllowedTools(List<String> allowedTools) {
        this.allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }

    public void setDocumentToolMode(DocumentToolMode documentToolMode) {
        this.documentToolMode = documentToolMode == null ? DocumentToolMode.FULL : documentToolMode;
    }
}
