package com.agent.editor.agent.v2.core.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    private String id;
    private String name;
    private String arguments;

    public ToolCall(String name, String arguments) {
        this(UUID.randomUUID().toString(), name, arguments);
    }
}
