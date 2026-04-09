package com.agent.editor.agent.tool.memory;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MemoryUpsertArguments {

    private String action;
    private String memoryType;
    private String memoryId;
    private String documentId;
    private String summary;
}
