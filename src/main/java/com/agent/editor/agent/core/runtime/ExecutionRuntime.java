package com.agent.editor.agent.core.runtime;

import com.agent.editor.agent.core.agent.Agent;
import com.agent.editor.agent.core.context.AgentRunContext;

public interface ExecutionRuntime {
    /**
     * 使用 runtime 默认的初始上下文执行 agent。
     *
     * @param agent 要执行的 agent
     * @param request 本次执行的静态请求参数
     * @return 执行结果，包含最终消息、内容和最终状态
     */
    ExecutionResult run(Agent agent, ExecutionRequest request);

    /**
     * 在调用方提供的初始上下文上执行 agent。
     *
     * @param agent 要执行的 agent
     * @param request 本次执行的静态请求参数
     * @param initialContext 调用方预先组装好的运行上下文
     * @return 执行结果，包含最终消息、内容和最终状态
     */
    default ExecutionResult run(Agent agent, ExecutionRequest request, AgentRunContext initialContext) {
        return run(agent, request);
    }
}
