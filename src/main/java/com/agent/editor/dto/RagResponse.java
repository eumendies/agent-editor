package com.agent.editor.dto;

import java.util.List;

public record RagResponse(
        String answer,
        List<RagCitation> citations
) {
}
