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
    private String workerId;

    public ToolContext(String taskId, String currentContent) {
        this(taskId, null, null, currentContent, null);
    }

    public ToolContext(String taskId,
                       String documentId,
                       String documentTitle,
                       String currentContent) {
        this(taskId, documentId, documentTitle, currentContent, null);
    }
}
