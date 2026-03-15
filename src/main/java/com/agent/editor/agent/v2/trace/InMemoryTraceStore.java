package com.agent.editor.agent.v2.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTraceStore implements TraceStore {

    private final Map<String, List<TraceRecord>> tracesByTask = new ConcurrentHashMap<>();

    @Override
    public void append(TraceRecord record) {
        tracesByTask.computeIfAbsent(record.taskId(), ignored -> Collections.synchronizedList(new ArrayList<>()))
                .add(record);
    }

    @Override
    public List<TraceRecord> getByTaskId(String taskId) {
        List<TraceRecord> traces = tracesByTask.get(taskId);
        if (traces == null) {
            return List.of();
        }
        synchronized (traces) {
            return List.copyOf(traces);
        }
    }
}
