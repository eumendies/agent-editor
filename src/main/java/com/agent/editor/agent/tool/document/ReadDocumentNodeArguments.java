package com.agent.editor.agent.tool.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadDocumentNodeArguments {

    private String nodeId;
    private String mode;
    private String blockId;
    private Boolean includeChildren;
}
