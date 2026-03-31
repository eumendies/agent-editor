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
    private HybridProperties hybrid = new HybridProperties();

    public MilvusProperties(String host, int port, String collectionName, int embeddingDimension) {
        this.host = host;
        this.port = port;
        this.collectionName = collectionName;
        this.embeddingDimension = embeddingDimension;
        this.hybrid = new HybridProperties();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HybridProperties {

        private RangeSearchProperties dense = new RangeSearchProperties();
        private RangeSearchProperties sparse = new RangeSearchProperties();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RangeSearchProperties {

        private Double radius;
        private Double rangeFilter;
    }
}
