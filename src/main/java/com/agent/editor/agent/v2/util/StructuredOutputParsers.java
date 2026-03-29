package com.agent.editor.agent.v2.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class StructuredOutputParsers {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private StructuredOutputParsers() {
    }

    public static <T> T parseJson(String text, Class<T> type) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(text, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static <T> T parseJsonWithMarkdownCleanup(String text, Class<T> type) {
        T direct = parseJson(text, type);
        if (direct != null) {
            return direct;
        }
        String cleaned = stripMarkdownCodeFence(text);
        if (cleaned == null || cleaned.equals(text)) {
            return null;
        }
        return parseJson(cleaned, type);
    }

    public static <T> T parseJsonOrThrow(String text, Class<T> type, String errorMessage) {
        T parsed = parseJsonWithMarkdownCleanup(text, type);
        if (parsed != null) {
            return parsed;
        }
        throw new IllegalArgumentException(errorMessage);
    }

    static String stripMarkdownCodeFence(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("```") || !trimmed.endsWith("```")) {
            return text;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return text;
        }
        int closingFence = trimmed.lastIndexOf("```");
        if (closingFence <= firstNewline) {
            return text;
        }
        return trimmed.substring(firstNewline + 1, closingFence).trim();
    }
}
