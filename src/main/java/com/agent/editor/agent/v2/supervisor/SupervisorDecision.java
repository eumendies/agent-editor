package com.agent.editor.agent.v2.supervisor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public sealed interface SupervisorDecision permits SupervisorDecision.AssignWorker, SupervisorDecision.Complete {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class AssignWorker implements SupervisorDecision {

        private String workerId;
        private String instruction;
        private String reasoning;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class Complete implements SupervisorDecision {

        private String finalContent;
        private String summary;
        private String reasoning;
    }
}
