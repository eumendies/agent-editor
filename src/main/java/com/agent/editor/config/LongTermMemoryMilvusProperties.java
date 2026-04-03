package com.agent.editor.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "agent.long-term-memory.milvus")
public class LongTermMemoryMilvusProperties {

    private String collectionName = "long_term_memory_v1";
    private int embeddingDimension = 1024;
}
