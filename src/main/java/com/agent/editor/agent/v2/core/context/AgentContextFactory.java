package com.agent.editor.agent.v2.core.context;

import com.agent.editor.agent.v2.task.TaskRequest;

/**
 * 负责组装上下文的工厂类
 */
public interface AgentContextFactory {

    AgentRunContext prepareInitialContext(TaskRequest request);

    ModelInvocationContext buildModelInvocationContext(AgentRunContext context);
}
