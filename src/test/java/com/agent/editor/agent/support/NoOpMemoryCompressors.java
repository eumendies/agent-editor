package com.agent.editor.agent.support;

import com.agent.editor.agent.core.memory.MemoryCompressionResult;
import com.agent.editor.agent.core.memory.MemoryCompressor;

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
