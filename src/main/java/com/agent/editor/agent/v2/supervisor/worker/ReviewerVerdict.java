package com.agent.editor.agent.v2.supervisor.worker;

/**
 * reviewer 对当前草稿的总体结论。
 */
public enum ReviewerVerdict {
    // PASS 表示 reviewer 认为当前答案可直接收口；REVISE 表示仍需继续 research/write。
    PASS,
    REVISE
}
