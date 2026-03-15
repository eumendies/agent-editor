package com.agent.editor.config;

import com.agent.editor.agent.v2.trace.DefaultTraceCollector;
import com.agent.editor.agent.v2.trace.InMemoryTraceStore;
import com.agent.editor.agent.v2.trace.TraceCollector;
import com.agent.editor.agent.v2.trace.TraceStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TraceConfig {

    @Bean
    public TraceStore traceStore() {
        return new InMemoryTraceStore();
    }

    @Bean
    public TraceCollector traceCollector(TraceStore traceStore) {
        return new DefaultTraceCollector(traceStore);
    }
}
