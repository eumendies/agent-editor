package com.agent.editor.agent.v2.core.agent;

import java.util.List;

public sealed interface Decision permits Decision.ToolCalls, Decision.Respond, Decision.Complete {

    record ToolCalls(List<ToolCall> calls, String reasoning) implements Decision {
    }

    record Respond(String message, String reasoning) implements Decision {
    }

    record Complete(String result, String reasoning) implements Decision {
    }
}
