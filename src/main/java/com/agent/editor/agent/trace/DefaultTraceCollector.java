package com.agent.editor.agent.trace;

/**
 * 追踪采集器的默认实现。
 * 当前只负责把高保真 trace 转交给 store，后续要接文件落盘或 OTel exporter 可以从这里扩展。
 */
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
