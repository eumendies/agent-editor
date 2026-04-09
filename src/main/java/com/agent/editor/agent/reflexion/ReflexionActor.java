package com.agent.editor.agent.reflexion;

import com.agent.editor.agent.core.agent.AgentType;
import com.agent.editor.agent.model.StreamingLLMInvoker;
import com.agent.editor.agent.react.ReactAgent;
import dev.langchain4j.model.chat.ChatModel;

public class ReflexionActor extends ReactAgent {

    public static ReflexionActor blocking(ChatModel chatModel, ReflexionActorContextFactory contextFactory) {
        return new ReflexionActor(chatModel, null, contextFactory);
    }

    public static ReflexionActor streaming(StreamingLLMInvoker streamingLLMInvoker, ReflexionActorContextFactory contextFactory) {
        return new ReflexionActor(null, streamingLLMInvoker, contextFactory);
    }

    private ReflexionActor(ChatModel chatModel,
                           StreamingLLMInvoker streamingLLMInvoker,
                           ReflexionActorContextFactory contextFactory) {
        // actor 直接复用 ReAct 的工具调用与执行能力，只把对外 agent type 切到 REFLEXION。
        super(chatModel, streamingLLMInvoker, contextFactory);
    }

    @Override
    public AgentType type() {
        return AgentType.REFLEXION;
    }
}
