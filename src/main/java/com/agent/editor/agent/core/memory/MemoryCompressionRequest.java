package com.agent.editor.agent.core.memory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryCompressionRequest {

    private ChatTranscriptMemory memory;
    private Integer compressionTriggerTotalTokens;
    private Integer compressionTargetTotalTokens;
    private Integer preserveLatestMessageCount;
    private Integer fallbackMaxMessageCount;
}
