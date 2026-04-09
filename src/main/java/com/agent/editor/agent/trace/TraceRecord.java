package com.agent.editor.agent.trace;

import com.agent.editor.agent.core.agent.AgentType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class TraceRecord {

    private String traceId;
    private String taskId;
    private Instant timestamp;
    private TraceCategory category;
    private String stage;
    private AgentType agentType;
    private String workerId;
    private Integer iteration;
    private Map<String, Object> payload = Map.of();

    public TraceRecord(String traceId,
                       String taskId,
                       Instant timestamp,
                       TraceCategory category,
                       String stage,
                       AgentType agentType,
                       String workerId,
                       Integer iteration,
                       Map<String, Object> payload) {
        this.traceId = traceId;
        this.taskId = taskId;
        this.timestamp = timestamp;
        this.category = category;
        this.stage = stage;
        this.agentType = agentType;
        this.workerId = workerId;
        this.iteration = iteration;
        setPayload(payload);
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
