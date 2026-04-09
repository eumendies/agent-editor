package com.agent.editor.agent.tool;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行时可见的运行上下文。
 * 这里只承载工具真正需要消费的任务与文档信息，不重复保存权限判定状态。
 */
@Data
@NoArgsConstructor
public class ToolContext {

    private String taskId;
    private String documentId;
    private String documentTitle;
    private String currentContent;

    public ToolContext(String taskId, String currentContent) {
        this(taskId, null, null, currentContent);
    }

    public ToolContext(String taskId,
                       String documentId,
                       String documentTitle,
                       String currentContent) {
        this.taskId = taskId;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.currentContent = currentContent;
    }
}
