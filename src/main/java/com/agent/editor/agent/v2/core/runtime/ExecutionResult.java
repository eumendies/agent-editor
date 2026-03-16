package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.state.ExecutionState;

public record ExecutionResult(String finalMessage, String finalContent, ExecutionState finalState) {

    public ExecutionResult(String finalMessage) {
        this(finalMessage, finalMessage, new ExecutionState(0, finalMessage));
    }

    public ExecutionResult(String finalMessage, String finalContent) {
        this(finalMessage, finalContent, new ExecutionState(0, finalContent));
    }
}
