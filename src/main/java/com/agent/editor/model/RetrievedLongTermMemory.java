package com.agent.editor.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrievedLongTermMemory {

    private String memoryId;
    private String memoryType;
    private String summary;
    private String relevanceReason;
    private String sourceTaskId;
    private String createdAt;
}
