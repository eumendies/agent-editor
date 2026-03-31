package com.agent.editor.agent.v2.memory;

import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import dev.langchain4j.model.chat.response.ChatResponse;

public final class ObservedTokenUsageRecorder {

    private ObservedTokenUsageRecorder() {
    }

    public static void record(AgentRunContext context, ChatResponse response) {
        if (context == null || response == null || response.tokenUsage() == null) {
            return;
        }
        if (!(context.getMemory() instanceof ChatTranscriptMemory transcriptMemory)) {
            return;
        }
        transcriptMemory.setLastObservedTotalTokens(response.tokenUsage().totalTokenCount());
    }
}
