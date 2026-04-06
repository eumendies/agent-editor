package com.agent.editor.agent.v2.supervisor.worker;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryWorkerSummary {

    private List<String> confirmedConstraints;
    private List<String> deprecatedConstraints;
    private List<String> activeRisks;
    private String guidanceForDownstreamWorkers;
}
