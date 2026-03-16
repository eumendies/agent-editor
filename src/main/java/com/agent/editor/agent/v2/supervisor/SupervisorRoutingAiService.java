package com.agent.editor.agent.v2.supervisor;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

interface SupervisorRoutingAiService {

    @SystemMessage("""
            You are a supervisor for a document editing workflow.
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
