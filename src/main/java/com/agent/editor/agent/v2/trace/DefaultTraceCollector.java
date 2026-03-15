package com.agent.editor.agent.v2.trace;

public class DefaultTraceCollector implements TraceCollector {

    private final TraceStore traceStore;

    public DefaultTraceCollector(TraceStore traceStore) {
        this.traceStore = traceStore;
    }

    @Override
    public void collect(TraceRecord record) {
        traceStore.append(record);
    }
}
