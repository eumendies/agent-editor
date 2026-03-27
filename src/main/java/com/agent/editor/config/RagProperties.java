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

    public RagProperties(int chunkSize, int chunkOverlap, int askTopK, int writeTopK, int retrieveTopK) {
        // 保持原 record 构造器的兜底行为，避免测试或手动 new 时把 0 当成有效配置写进去。
        this.chunkSize = chunkSize == 0 ? 500 : chunkSize;
        this.chunkOverlap = chunkOverlap == 0 ? 80 : chunkOverlap;
        this.askTopK = askTopK == 0 ? 5 : askTopK;
        this.writeTopK = writeTopK == 0 ? 8 : writeTopK;
        this.retrieveTopK = retrieveTopK == 0 ? 12 : retrieveTopK;
    }
}
