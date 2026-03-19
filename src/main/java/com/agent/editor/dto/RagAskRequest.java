package com.agent.editor.dto;

import java.util.List;

public record RagAskRequest(
        String question,
        List<String> documentIds
) {
}
