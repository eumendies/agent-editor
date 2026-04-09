package com.agent.editor.agent.tool.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatchDocumentNodeArguments {

    private String documentVersion;
    private String nodeId;
    private String blockId;
    private String baseHash;
    private String operation;
    private String content;
    private String reason;
}
