package com.agent.editor.agent.v2.tool.document;

import com.fasterxml.jackson.databind.ObjectMapper;

final class ToolArgumentDecoder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ToolArgumentDecoder() {
    }

    static <T> T decode(String arguments, Class<T> argumentType, String toolName) {
        try {
            return OBJECT_MAPPER.readValue(arguments, argumentType);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse tool arguments for " + toolName, exception);
        }
    }
}
