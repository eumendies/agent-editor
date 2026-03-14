package com.agent.editor.agent.v2.orchestration;

public sealed interface SupervisorDecision permits SupervisorDecision.AssignWorker, SupervisorDecision.Complete {

    record AssignWorker(String workerId, String instruction, String reasoning) implements SupervisorDecision {
    }

    record Complete(String finalContent, String summary, String reasoning) implements SupervisorDecision {
    }
}
