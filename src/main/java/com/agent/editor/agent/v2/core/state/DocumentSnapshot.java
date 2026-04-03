package com.agent.editor.agent.v2.core.state;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档执行入口使用的正文快照，描述当前任务看到的原始文档内容。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSnapshot {

    // 文档唯一标识。
    private String documentId;
    // 文档标题，通常用于上下文提示。
    private String title;
    // 任务启动时的完整正文内容。
    private String content;
}
