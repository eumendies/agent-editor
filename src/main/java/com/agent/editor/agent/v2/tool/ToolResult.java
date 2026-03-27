package com.agent.editor.agent.v2.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    private String message;
    private String updatedContent;

    public ToolResult(String message) {
        this(message, null);
    }
}
