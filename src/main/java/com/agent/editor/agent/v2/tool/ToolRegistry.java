package com.agent.editor.agent.v2.tool;

import java.util.HashMap;
import java.util.Map;

public class ToolRegistry {

    private final Map<String, ToolHandler> handlers = new HashMap<>();

    public void register(ToolHandler handler) {
        handlers.put(handler.name(), handler);
    }

    public ToolHandler get(String name) {
        return handlers.get(name);
    }
}
