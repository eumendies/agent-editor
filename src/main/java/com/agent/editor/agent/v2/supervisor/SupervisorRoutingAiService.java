package com.agent.editor.agent.v2.supervisor;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

interface SupervisorRoutingAiService {

    @SystemMessage("""
            You are a supervisor for a document editing workflow.
            Choose one of the candidate workers or complete the task.

            Return a JSON object matching this schema:
            - action: ASSIGN_WORKER or COMPLETE
            - workerId: one of the candidate worker ids when action is ASSIGN_WORKER, otherwise null
            - instruction: required when action is ASSIGN_WORKER
            - summary: optional when action is ASSIGN_WORKER, required when action is COMPLETE
            - finalContent: optional when action is ASSIGN_WORKER, required when action is COMPLETE
            - reasoning: concise explanation of the choice
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
