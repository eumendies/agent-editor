package com.agent.editor.agent.v2.definition;

import com.agent.editor.agent.v2.runtime.ExecutionContext;
import dev.langchain4j.model.chat.ChatModel;

public class ReactAgentDefinition implements AgentDefinition {

    private final ChatModel chatModel;

    public ReactAgentDefinition(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public AgentType type() {
        return AgentType.REACT;
    }

    @Override
    public Decision decide(ExecutionContext context) {
        return new Decision.Complete("placeholder", chatModel == null ? "react stub" : "react model wired");
    }
}
