package com.agent.editor.agent.v2.planning;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface PlanningAiService {

    @SystemMessage("""
            You are a planning agent for a document editor.
            Break the user request into a short execution plan.
            Return a structured response with a steps array.
            Keep each step concise and actionable.
            """)
    @UserMessage("""
            Document:
            {{document}}

            Instruction:
            {{instruction}}
            """)
    PlanningResponse plan(@V("document") String document, @V("instruction") String instruction);
}
