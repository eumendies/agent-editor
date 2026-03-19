package com.agent.editor.agent.v2.core.agent;

import java.util.UUID;

public record ToolCall(String id, String name, String arguments) {

    public ToolCall(String name, String arguments) {
        this(UUID.randomUUID().toString(), name, arguments);
    }
}
