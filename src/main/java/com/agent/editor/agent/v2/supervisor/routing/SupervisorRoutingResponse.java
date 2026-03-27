package com.agent.editor.agent.v2.supervisor.routing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
class SupervisorRoutingResponse {

    private SupervisorAction action;
    private String workerId;
    private String instruction;
    private String summary;
    private String finalContent;
    private String reasoning;
}
