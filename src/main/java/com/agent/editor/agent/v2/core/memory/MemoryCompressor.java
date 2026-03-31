package com.agent.editor.agent.v2.core.memory;

public interface MemoryCompressor {

    MemoryCompressionResult compress(MemoryCompressionRequest request);

    default ExecutionMemory compressOrOriginal(ExecutionMemory memory) {
        if (!(memory instanceof ChatTranscriptMemory transcriptMemory)) {
            return memory;
        }
        return compressOrOriginal(transcriptMemory);
    }

    default ChatTranscriptMemory compressOrOriginal(ChatTranscriptMemory memory) {
        MemoryCompressionResult result = compress(new MemoryCompressionRequest(
                memory,
                null,
                null,
                null,
                null
        ));
        return result == null || result.getMemory() == null ? memory : result.getMemory();
    }
}
