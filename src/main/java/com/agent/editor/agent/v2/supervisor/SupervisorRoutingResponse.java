package com.agent.editor.agent.v2.supervisor;

record SupervisorRoutingResponse(
        SupervisorAction action,
        String workerId,
        String instruction,
        String summary,
        String finalContent,
        String reasoning
) {
}
