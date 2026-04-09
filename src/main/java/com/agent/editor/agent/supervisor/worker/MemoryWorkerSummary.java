package com.agent.editor.agent.supervisor.worker;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * memory worker 的结构化完成结果。
 * 这份摘要会被折叠进 supervisor 会话记忆，供后续 writer/reviewer 直接消费。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryWorkerSummary {

    private List<String> confirmedConstraints;
    private List<String> deprecatedConstraints;
    private List<String> activeRisks;
    private String guidanceForDownstreamWorkers;
}
