package com.agent.editor.agent.v2.tool.memory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryUpsertResult {

    private String action;
    private String memoryId;
    private String memoryType;
    private String documentId;
    private String summary;
}
