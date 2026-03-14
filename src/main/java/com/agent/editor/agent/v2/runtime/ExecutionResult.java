package com.agent.editor.agent.v2.runtime;

public record ExecutionResult(String finalMessage, String finalContent) {

    public ExecutionResult(String finalMessage) {
        this(finalMessage, finalMessage);
    }
}
