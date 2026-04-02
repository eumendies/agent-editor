package com.agent.editor.agent.v2.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolContext {

    private String taskId;
    private String documentId;
    private String documentTitle;
    private String currentContent;

    public ToolContext(String taskId, String currentContent) {
        this(taskId, null, null, currentContent);
    }
}
