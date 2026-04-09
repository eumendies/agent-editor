package com.agent.editor.agent.tool.memory;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MemorySearchArguments {

    private String query;
    private String documentId;
    private Integer topK;
}
