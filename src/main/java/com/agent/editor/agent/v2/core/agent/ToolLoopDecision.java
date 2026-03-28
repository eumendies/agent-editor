package com.agent.editor.agent.v2.core.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public sealed interface ToolLoopDecision permits ToolLoopDecision.ToolCalls, ToolLoopDecision.Respond, ToolLoopDecision.Complete {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class ToolCalls implements ToolLoopDecision {

        private List<ToolCall> calls;
        private String reasoning;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class Respond implements ToolLoopDecision {

        private String message;
        private String reasoning;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class Complete<T> implements ToolLoopDecision {
        private T result;
        private String reasoning;
    }
}
