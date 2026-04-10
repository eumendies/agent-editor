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

}
