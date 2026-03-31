package com.agent.editor.agent.v2.core.memory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryCompressionResult {

    private ChatTranscriptMemory memory;
    private boolean compressed;
    private String reason;
}
