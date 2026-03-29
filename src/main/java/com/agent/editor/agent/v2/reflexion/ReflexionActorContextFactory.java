package com.agent.editor.agent.v2.reflexion;

import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import com.agent.editor.agent.v2.react.ReactAgentContextFactory;
import com.agent.editor.agent.v2.task.TaskRequest;

public class ReflexionActorContextFactory extends ReactAgentContextFactory {

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
