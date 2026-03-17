package com.agent.editor.agent.v2.reflexion;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.react.ReactAgentDefinition;
import com.agent.editor.agent.v2.trace.TraceCollector;
import dev.langchain4j.model.chat.ChatModel;

public class ReflexionActorDefinition extends ReactAgentDefinition {

    public ReflexionActorDefinition(ChatModel chatModel, TraceCollector traceCollector) {
        super(chatModel, traceCollector);
    }

    @Override
    public AgentType type() {
        return AgentType.REFLEXION;
    }
}
