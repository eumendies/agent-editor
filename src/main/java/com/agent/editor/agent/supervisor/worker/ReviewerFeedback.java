package com.agent.editor.agent.supervisor.worker;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * reviewer 输出的标准审查反馈。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewerFeedback {

    // reviewer 的结构化审查结果，supervisor 依赖这些字段做硬规则收口和候选 worker 重排。
    private ReviewerVerdict verdict;
    private boolean instructionSatisfied;
    private boolean evidenceGrounded;
    private List<String> unsupportedClaims;
    private List<String> missingRequirements;
    private String feedback;
    private String reasoning;
}
