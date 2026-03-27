package com.agent.editor.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {

    private String host;
    private int port;
    private String collectionName;
    private int embeddingDimension;
}
