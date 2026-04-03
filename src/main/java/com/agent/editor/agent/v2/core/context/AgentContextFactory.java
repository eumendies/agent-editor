package com.agent.editor.agent.v2.core.context;

import com.agent.editor.agent.v2.task.TaskRequest;

/**
 * 负责组装上下文的工厂类
 */
public interface AgentContextFactory {

    /**
     * 为一次全新的 agent 执行构造初始上下文。
     *
     * @param request 任务输入
     * @return 供 runtime 启动执行的初始状态
     */
    AgentRunContext prepareInitialContext(TaskRequest request);

    /**
     * 把运行时上下文转换为模型调用所需的消息、工具和结构化输出配置。
     *
     * @param context 当前 agent 运行态
     * @return 模型调用视角下的上下文
     */
    ModelInvocationContext buildModelInvocationContext(AgentRunContext context);
}
