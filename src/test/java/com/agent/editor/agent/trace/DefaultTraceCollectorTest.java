package com.agent.editor.agent.trace;

import com.agent.editor.agent.core.agent.AgentType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultTraceCollectorTest {

    @Test
    void shouldStoreTraceRecordsByTaskInInsertionOrder() {
        TraceStore store = new InMemoryTraceStore();
        TraceCollector collector = new DefaultTraceCollector(store);

        TraceRecord first = new TraceRecord(
                "trace-1",
                "task-1",
                Instant.parse("2026-03-15T09:00:00Z"),
                TraceCategory.MODEL_REQUEST,
                "react",
                AgentType.REACT,
                null,
                0,
                Map.of("prompt", "first prompt")
        );
        TraceRecord second = new TraceRecord(
                "trace-2",
                "task-1",
                Instant.parse("2026-03-15T09:00:01Z"),
                TraceCategory.MODEL_RESPONSE,
                "react",
                AgentType.REACT,
                null,
                0,
                Map.of("response", "second response")
        );

        collector.collect(first);
        collector.collect(second);

        List<TraceRecord> traces = store.getByTaskId("task-1");

        assertEquals(List.of(first, second), traces);
    }
}
