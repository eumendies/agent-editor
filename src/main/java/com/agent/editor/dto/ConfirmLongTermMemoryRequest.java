package com.agent.editor.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ConfirmLongTermMemoryRequest {

    private List<String> candidateIds = List.of();
}
