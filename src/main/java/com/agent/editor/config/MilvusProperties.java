package com.agent.editor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "milvus")
public record MilvusProperties(
        String host,
        int port,
        String collectionName,
        int embeddingDimension
) {
}
