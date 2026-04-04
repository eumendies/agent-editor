package com.agent.editor.agent.v2.memory;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface LongTermMemoryExtractionAiService {

    @SystemMessage("""
            You extract candidate long-term memory for a document-editing agent.
            Review the chat transcript and return only stable, reusable memories.

            Return JSON with exactly two arrays:
            - userProfiles: stable user preferences that should apply across future tasks
            - documentDecisions: confirmed or reusable document-specific decisions that should apply to the current document

            Each array item must contain only:
            - summary: a concise standalone sentence

            Rules:
            - Do not include temporary task steps, generic summaries, or repeated transcript text.
            - userProfiles should only contain durable preferences or constraints.
            - documentDecisions should only contain durable document-specific choices or constraints.
            - If nothing is worth remembering, return empty arrays.
            """)
    @UserMessage("""
            Chat transcript:
            {{transcript}}
            """)
    LongTermMemoryExtractionResponse extract(@V("transcript") String transcript);
}
