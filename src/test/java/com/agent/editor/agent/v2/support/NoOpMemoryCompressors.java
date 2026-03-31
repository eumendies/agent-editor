package com.agent.editor.agent.v2.support;

import com.agent.editor.agent.v2.core.memory.MemoryCompressionResult;
import com.agent.editor.agent.v2.core.memory.MemoryCompressor;

public final class NoOpMemoryCompressors {

    private NoOpMemoryCompressors() {
    }

    public static MemoryCompressor noop() {
        return request -> new MemoryCompressionResult(
                request == null ? null : request.getMemory(),
                false,
                "noop"
        );
    }
}
