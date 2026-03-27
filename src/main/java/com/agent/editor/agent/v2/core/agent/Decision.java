package com.agent.editor.agent.v2.core.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public sealed interface Decision permits Decision.ToolCalls, Decision.Respond, Decision.Complete {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class ToolCalls implements Decision {

        private List<ToolCall> calls;
        private String reasoning;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class Respond implements Decision {

        private String message;
        private String reasoning;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class Complete implements Decision {

        private String result;
        private String reasoning;
    }
}
