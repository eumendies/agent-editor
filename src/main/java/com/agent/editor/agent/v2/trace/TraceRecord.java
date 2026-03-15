package com.agent.editor.agent.v2.trace;

import com.agent.editor.agent.v2.definition.AgentType;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record TraceRecord(String traceId,
                          String taskId,
                          Instant timestamp,
                          TraceCategory category,
                          String stage,
                          AgentType agentType,
                          String workerId,
                          Integer iteration,
                          Map<String, Object> payload) {

    public TraceRecord {
        payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
