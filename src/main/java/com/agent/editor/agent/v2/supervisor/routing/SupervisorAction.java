package com.agent.editor.agent.v2.supervisor.routing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

enum SupervisorAction {
    ASSIGN_WORKER("assign_worker"),
    COMPLETE("complete");

    private final String wireValue;

    SupervisorAction(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static SupervisorAction fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        for (SupervisorAction action : values()) {
            if (action.name().equalsIgnoreCase(value) || action.wireValue.equalsIgnoreCase(value)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unsupported supervisor action: " + value);
    }
}
