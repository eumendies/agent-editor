package com.agent.editor.agent.v2.supervisor.routing;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

interface SupervisorRoutingAiService {

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
