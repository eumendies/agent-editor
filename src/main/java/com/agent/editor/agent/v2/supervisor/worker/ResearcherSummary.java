package com.agent.editor.agent.v2.supervisor.worker;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResearcherSummary {

    private String evidenceSummary;
    private String limitations;
    private List<String> uncoveredPoints;
}
