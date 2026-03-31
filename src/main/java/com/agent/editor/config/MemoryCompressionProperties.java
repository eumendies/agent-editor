package com.agent.editor.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "agent.memory-compression")
public class MemoryCompressionProperties {

    private int triggerTotalTokens = 12000;
    private int preserveLatestMessageCount = 3;
    private int fallbackMaxMessageCount = 40;

    public MemoryCompressionProperties(int triggerTotalTokens,
                                       int preserveLatestMessageCount,
                                       int fallbackMaxMessageCount) {
        this.triggerTotalTokens = triggerTotalTokens == 0 ? 12000 : triggerTotalTokens;
        this.preserveLatestMessageCount = preserveLatestMessageCount == 0 ? 3 : preserveLatestMessageCount;
        this.fallbackMaxMessageCount = fallbackMaxMessageCount == 0 ? 40 : fallbackMaxMessageCount;
    }
}
