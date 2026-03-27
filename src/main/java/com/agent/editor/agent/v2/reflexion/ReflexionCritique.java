package com.agent.editor.agent.v2.reflexion;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReflexionCritique {

    private ReflexionVerdict verdict;
    private String feedback;
    private String reasoning;
}
