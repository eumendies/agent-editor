package com.agent.editor.controller;

import com.agent.editor.agent.v2.trace.TraceRecord;
import com.agent.editor.agent.v2.trace.TraceStore;
import com.agent.editor.dto.TraceSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/agent/task/{taskId}/trace")
public class TraceController {

    private final TraceStore traceStore;

    public TraceController(TraceStore traceStore) {
        this.traceStore = traceStore;
    }

    @GetMapping
    public ResponseEntity<List<TraceRecord>> getTrace(@PathVariable String taskId) {
        return ResponseEntity.ok(traceStore.getByTaskId(taskId));
    }

    @GetMapping("/summary")
    public ResponseEntity<TraceSummaryResponse> getTraceSummary(@PathVariable String taskId) {
        List<TraceRecord> traces = traceStore.getByTaskId(taskId);
        TraceSummaryResponse response = new TraceSummaryResponse();
        response.setTaskId(taskId);
        response.setTotalRecords(traces.size());
        response.setCategoryCounts(traces.stream()
                .collect(Collectors.groupingBy(trace -> trace.getCategory().name(), Collectors.counting())));
        response.setStages(traces.stream().map(TraceRecord::getStage).toList());
        return ResponseEntity.ok(response);
    }
}
