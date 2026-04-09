package com.agent.editor.agent.reflexion;

import com.agent.editor.agent.core.memory.MemoryCompressor;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.context.CompressContextMemory;
import com.agent.editor.agent.core.state.ExecutionStage;
import com.agent.editor.agent.react.ReactAgentContextFactory;
import com.agent.editor.agent.task.TaskRequest;

public class ReflexionActorContextFactory extends ReactAgentContextFactory {

    public ReflexionActorContextFactory(MemoryCompressor memoryCompressor) {
        super(memoryCompressor);
    }

    @CompressContextMemory
    public AgentRunContext prepareRevisionContext(TaskRequest request,
                                                  AgentRunContext actorState,
                                                  int round,
                                                  ReflexionCritique critique) {
        return actorState
                .appendMemory(new ChatMessage.UserChatMessage(formatCritique(round, critique)))
                .withStage(ExecutionStage.RUNNING);
    }

    private String formatCritique(int round, ReflexionCritique critique) {
        return """
                Reflection critique:
                {"round":%d,"verdict":"%s","feedback":"%s","reasoning":"%s"}
                """.formatted(
                round,
                critique.getVerdict().name(),
                escapeJson(critique.getFeedback()),
                escapeJson(critique.getReasoning())
        );
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
