package com.agent.editor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingDocumentChange {

    private String documentId;
    private String taskId;
    private String originalContent;
    private String proposedContent;
    private String diffHtml;
    private LocalDateTime createdAt;
}
