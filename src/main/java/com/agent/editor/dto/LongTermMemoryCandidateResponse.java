package com.agent.editor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LongTermMemoryCandidateResponse {

    private String candidateId;
    private String memoryType;
    private String summary;
    private String documentId;
}
