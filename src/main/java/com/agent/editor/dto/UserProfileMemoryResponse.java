package com.agent.editor.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserProfileMemoryResponse {

    private String memoryId;
    private String memoryType;
    private String summary;
}
