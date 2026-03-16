package com.agent.editor.agent.v2.core.state;

public sealed interface ExecutionMessage permits ExecutionMessage.SystemExecutionMessage,
        ExecutionMessage.UserExecutionMessage,
        ExecutionMessage.AiExecutionMessage,
        ExecutionMessage.ToolExecutionResultExecutionMessage {

    String text();

    record SystemExecutionMessage(String text) implements ExecutionMessage {
    }

    record UserExecutionMessage(String text) implements ExecutionMessage {
    }

    record AiExecutionMessage(String text) implements ExecutionMessage {
    }

    record ToolExecutionResultExecutionMessage(String text) implements ExecutionMessage {
    }
}
