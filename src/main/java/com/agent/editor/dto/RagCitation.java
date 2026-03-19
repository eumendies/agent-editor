package com.agent.editor.dto;

public record RagCitation(
        int index,
        String fileName,
        int chunkIndex,
        String excerpt
) {
}
