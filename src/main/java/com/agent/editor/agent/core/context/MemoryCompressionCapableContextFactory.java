package com.agent.editor.agent.core.context;

import com.agent.editor.agent.core.memory.MemoryCompressor;

public interface MemoryCompressionCapableContextFactory {

    MemoryCompressor memoryCompressor();
}
