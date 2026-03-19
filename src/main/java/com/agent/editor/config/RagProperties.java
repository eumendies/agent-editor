package com.agent.editor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        int chunkSize,
        int chunkOverlap,
        int askTopK,
        int writeTopK,
        int retrieveTopK
) {

    public RagProperties {
        chunkSize = chunkSize == 0 ? 500 : chunkSize;
        chunkOverlap = chunkOverlap == 0 ? 80 : chunkOverlap;
        askTopK = askTopK == 0 ? 5 : askTopK;
        writeTopK = writeTopK == 0 ? 8 : writeTopK;
        retrieveTopK = retrieveTopK == 0 ? 12 : retrieveTopK;
    }
}
