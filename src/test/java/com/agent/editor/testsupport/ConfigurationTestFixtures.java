package com.agent.editor.testsupport;

import com.agent.editor.config.DocumentToolModeProperties;
import com.agent.editor.config.MemoryCompressionProperties;
import com.agent.editor.config.RagProperties;

public final class ConfigurationTestFixtures {

    private ConfigurationTestFixtures() {
    }

    public static RagProperties ragProperties(int chunkSize, int chunkOverlap, int askTopK, int writeTopK, int retrieveTopK) {
        RagProperties properties = new RagProperties();
        properties.setChunkSize(chunkSize);
        properties.setChunkOverlap(chunkOverlap);
        properties.setAskTopK(askTopK);
        properties.setWriteTopK(writeTopK);
        properties.setRetrieveTopK(retrieveTopK);
        return properties;
    }

    public static MemoryCompressionProperties memoryCompressionProperties() {
        return new MemoryCompressionProperties();
    }

    public static MemoryCompressionProperties memoryCompressionProperties(int triggerTotalTokens,
                                                                         int preserveLatestMessageCount,
                                                                         int fallbackMaxMessageCount) {
        MemoryCompressionProperties properties = new MemoryCompressionProperties();
        properties.setTriggerTotalTokens(triggerTotalTokens);
        properties.setPreserveLatestMessageCount(preserveLatestMessageCount);
        properties.setFallbackMaxMessageCount(fallbackMaxMessageCount);
        return properties;
    }

    public static DocumentToolModeProperties documentToolModeProperties(int longDocumentThresholdTokens) {
        DocumentToolModeProperties properties = new DocumentToolModeProperties();
        properties.setLongDocumentThresholdTokens(longDocumentThresholdTokens);
        return properties;
    }
}
