package com.agent.editor.agent.v2.reflexion;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.react.ReactAgent;
import dev.langchain4j.model.chat.ChatModel;

public class ReflexionActor extends ReactAgent {

    public ReflexionActor(ChatModel chatModel) {
        // actor 直接复用 ReAct 的工具调用与执行能力，只把对外 agent type 切到 REFLEXION。
        super(chatModel);
    }

    @Override
    public AgentType type() {
        return AgentType.REFLEXION;
    }
}
