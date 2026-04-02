package com.agent.editor.agent.v2.core.context;

import com.agent.editor.agent.v2.core.memory.MemoryCompressor;

public interface MemoryCompressionCapableContextFactory {

    MemoryCompressor memoryCompressor();
}
