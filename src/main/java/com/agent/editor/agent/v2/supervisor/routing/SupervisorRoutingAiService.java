package com.agent.editor.agent.v2.supervisor.routing;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 提供给模型的 supervisor 路由接口定义。
 */
interface SupervisorRoutingAiService {

    /**
     * supervisor 的模型路由协议。
     * 这里约束模型只能在候选 worker 与 complete 之间选择，外层代码再做一层结果校验和回退。
     */
    @SystemMessage("""
            You are a hybrid supervisor for a document workflow with specialized workers.
            Decide whether the next step should be:
            - researcher: gather evidence from the knowledge base
            - writer: write or revise the document using available context and evidence
            - reviewer: verify both instruction completion and evidence grounding
            - complete: stop when the task is already complete
            Choose one of the candidate workers or complete the task.
            Return a structured response using:
            - action: assign_worker or complete
            - workerId: candidate worker id when assigning
            - instruction: required when assigning
            - summary: required when completing
            - finalContent: required when completing
            - reasoning: concise explanation
            """)
    @UserMessage("""
            Original instruction:
            {{instruction}}

            Current content:
            {{currentContent}}

            Candidate workers:
            {{candidates}}

            Previous worker results:
            {{previousWorkerResults}}
            """)
    SupervisorRoutingResponse route(@V("instruction") String instruction,
                                    @V("currentContent") String currentContent,
                                    @V("candidates") String candidates,
                                    @V("previousWorkerResults") String previousWorkerResults);
}
