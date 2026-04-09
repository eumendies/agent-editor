package com.agent.editor.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolInvocation {

    private String toolName;
    private String arguments;
}
