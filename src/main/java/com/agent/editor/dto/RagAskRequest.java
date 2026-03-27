package com.agent.editor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagAskRequest {

    private String question;
    private List<String> documentIds;
}
