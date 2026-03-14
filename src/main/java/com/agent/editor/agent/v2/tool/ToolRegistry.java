package com.agent.editor.agent.v2.tool;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolRegistry {

    private final Map<String, ToolHandler> handlers = new HashMap<>();

    public void register(ToolHandler handler) {
        handlers.put(handler.name(), handler);
    }

    public ToolHandler get(String name) {
        return handlers.get(name);
    }

    public List<ToolSpecification> specifications() {
        return handlers.values().stream()
                .map(ToolHandler::specification)
                .toList();
    }
}
