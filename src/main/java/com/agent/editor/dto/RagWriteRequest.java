package com.agent.editor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagWriteRequest {

    private String instruction;
    private String taskType;
    private List<String> documentIds;
}
