package com.agent.editor.agent.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第一版 trace store 只做进程内存持有，方便本地调试和页面查看。
 * 它不是持久化存储，重启后 trace 会丢失。
 */
public class InMemoryTraceStore implements TraceStore {

    private final Map<String, List<TraceRecord>> tracesByTask = new ConcurrentHashMap<>();

    @Override
    public void append(TraceRecord record) {
        tracesByTask.computeIfAbsent(record.getTaskId(), ignored -> Collections.synchronizedList(new ArrayList<>()))
                .add(record);
    }

    @Override
    public List<TraceRecord> getByTaskId(String taskId) {
        List<TraceRecord> traces = tracesByTask.get(taskId);
        if (traces == null) {
            return List.of();
        }
        // 返回副本而不是内部列表，避免查询方把内存里的原始 trace 结构改坏。
        synchronized (traces) {
            return List.copyOf(traces);
        }
    }
}
