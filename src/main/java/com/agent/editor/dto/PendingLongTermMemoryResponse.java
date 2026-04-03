package com.agent.editor.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class PendingLongTermMemoryResponse {

    private String taskId;
    private List<LongTermMemoryCandidateResponse> candidates = List.of();
}
