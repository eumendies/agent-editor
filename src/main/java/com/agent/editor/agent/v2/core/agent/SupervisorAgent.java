package com.agent.editor.agent.v2.core.agent;

import com.agent.editor.agent.v2.core.context.SupervisorContext;

public interface SupervisorAgent extends Agent {
    /**
     * 在 supervisor 专用上下文中做一次路由决策。
     *
     * @param context 当前轮次可见的文档、worker 候选集和历史 worker 结果
     * @return 分派 worker 或直接完成任务的决策结果
     */
    SupervisorDecision decide(SupervisorContext context);
}
