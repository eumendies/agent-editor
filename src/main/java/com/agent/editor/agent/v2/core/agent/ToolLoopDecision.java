package com.agent.editor.agent.v2.core.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * tool-loop agent 单轮输出的结构化决策，表示调工具、继续回复或直接完成。
 */
public sealed interface ToolLoopDecision permits ToolLoopDecision.ToolCalls, ToolLoopDecision.Respond, ToolLoopDecision.Complete {

    /**
     * 要求 runtime 执行一组工具调用。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class ToolCalls implements ToolLoopDecision {

        // 需要依次执行的工具调用列表。
        private List<ToolCall> calls;
        // 模型决定调用工具的理由。
        private String reasoning;
    }

    /**
     * 返回一段中间回复，但尚未结束整个任务。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class Respond implements ToolLoopDecision {

        // 返回给上层或用户的消息。
        private String message;
        // 模型给出该回复的理由。
        private String reasoning;
    }

    /**
     * 当前 tool-loop 已经得到最终结果，可以结束执行。
     *
     * @param <T> 完成态携带的结构化结果类型
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class Complete<T> implements ToolLoopDecision {
        // 最终结构化结果。
        private T result;
        // 模型判定完成的理由。
        private String reasoning;
    }
}
