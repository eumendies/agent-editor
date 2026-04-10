package com.agent.editor.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "agent.document-tool-mode")
public class DocumentToolModeProperties {

    private int longDocumentThresholdTokens = 4_000;

}
