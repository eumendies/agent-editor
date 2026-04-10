package com.agent.editor.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private int chunkSize = 500;
    private int chunkOverlap = 80;
    private int askTopK = 5;
    private int writeTopK = 8;
    private int retrieveTopK = 12;

}
