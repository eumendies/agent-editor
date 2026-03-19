package com.agent.editor.dto;

import java.util.List;

public record RagWriteRequest(
        String instruction,
        String taskType,
        List<String> documentIds
) {
}
