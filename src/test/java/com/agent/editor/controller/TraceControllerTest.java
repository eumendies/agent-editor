package com.agent.editor.controller;

import com.agent.editor.agent.v2.definition.AgentType;
import com.agent.editor.agent.v2.trace.InMemoryTraceStore;
import com.agent.editor.agent.v2.trace.TraceCategory;
import com.agent.editor.agent.v2.trace.TraceRecord;
import com.agent.editor.agent.v2.trace.TraceStore;
import com.agent.editor.dto.TraceSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TraceControllerTest {

    @Test
    void shouldReturnFullTraceForTask() {
        TraceStore traceStore = new InMemoryTraceStore();
        TraceRecord record = new TraceRecord(
                "trace-1",
                "task-1",
                Instant.parse("2026-03-15T02:00:00Z"),
                TraceCategory.MODEL_REQUEST,
                "react.model.request",
                AgentType.REACT,
                null,
                0,
                Map.of("prompt", "rewrite this")
        );
        traceStore.append(record);
        TraceController controller = new TraceController(traceStore);

        ResponseEntity<List<TraceRecord>> response = controller.getTrace("task-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(List.of(record), response.getBody());
    }

    @Test
    void shouldReturnTraceSummaryForTask() {
        TraceStore traceStore = new InMemoryTraceStore();
        traceStore.append(new TraceRecord(
                "trace-1",
                "task-2",
                Instant.parse("2026-03-15T02:00:00Z"),
                TraceCategory.MODEL_REQUEST,
                "react.model.request",
                AgentType.REACT,
                null,
                0,
                Map.of("prompt", "rewrite this")
        ));
        traceStore.append(new TraceRecord(
                "trace-2",
                "task-2",
                Instant.parse("2026-03-15T02:00:01Z"),
                TraceCategory.TOOL_RESULT,
                "runtime.tool.result",
                AgentType.REACT,
                null,
                0,
                Map.of("message", "done")
        ));
        TraceController controller = new TraceController(traceStore);

        ResponseEntity<TraceSummaryResponse> response = controller.getTraceSummary("task-2");

        assertEquals(200, response.getStatusCode().value());
        TraceSummaryResponse body = response.getBody();
        assertEquals("task-2", body.getTaskId());
        assertEquals(2, body.getTotalRecords());
        assertEquals(1L, body.getCategoryCounts().get("MODEL_REQUEST"));
        assertEquals(1L, body.getCategoryCounts().get("TOOL_RESULT"));
        assertEquals(List.of("react.model.request", "runtime.tool.result"), body.getStages());
    }
}
