package com.agent.editor.agent.memory;

import com.agent.editor.config.MemoryCompressionProperties;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.core.memory.MemoryCompressionRequest;
import com.agent.editor.agent.core.memory.MemoryCompressionResult;
import com.agent.editor.agent.core.memory.MemoryCompressor;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ModelBasedMemoryCompressor implements MemoryCompressor {

    private static final String COMPRESSED_MEMORY_MARKER = "[Compressed Memory Summary %d/%d]";

    private final ChatModel chatModel;
    private final MemoryCompressionProperties properties;

    public ModelBasedMemoryCompressor(ChatModel chatModel) {
        this(chatModel, new MemoryCompressionProperties());
    }

    public ModelBasedMemoryCompressor(ChatModel chatModel,
                                      MemoryCompressionProperties properties) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public MemoryCompressionResult compress(MemoryCompressionRequest request) {
        if (request == null || request.getMemory() == null) {
            return new MemoryCompressionResult(null, false, "missing_memory");
        }
        ChatTranscriptMemory memory = request.getMemory();
        if (!shouldCompress(request, memory)) {
            return new MemoryCompressionResult(memory, false, "below_threshold");
        }

        List<ChatMessage> messages = memory.getMessages();
        Integer requestedPreserveCount = request.getPreserveLatestMessageCount();
        int preserveCount = requestedPreserveCount != null && requestedPreserveCount > 0
                ? requestedPreserveCount
                : properties.getPreserveLatestMessageCount();
        int compressibleCount = Math.max(0, messages.size() - preserveCount);
        if (compressibleCount == 0) {
            return new MemoryCompressionResult(memory, false, "insufficient_history");
        }

        List<ChatMessage> compressiblePrefix = messages.subList(0, compressibleCount);
        List<ChatMessage> preservedSuffix = messages.subList(compressibleCount, messages.size());
        try {
            // 最近 3 条消息需要保留原始形态，避免当前轮次依赖的局部上下文被摘要洗平。
            List<String> summarySections = summarizeWithModel(compressiblePrefix);
            if (summarySections == null || summarySections.isEmpty()) {
                return new MemoryCompressionResult(memory, false, "empty_summary");
            }

            List<ChatMessage> compressedMessages = new ArrayList<>();
            // 被压缩区里的工具调用和工具结果只保留为摘要文本，避免后续 prompt 再次膨胀。
            for (int index = 0; index < summarySections.size(); index++) {
                compressedMessages.add(new ChatMessage.AiChatMessage(
                        formatCompressedSummary(index + 1, summarySections.size(), summarySections.get(index))
                ));
            }
            compressedMessages.addAll(preservedSuffix);

            return new MemoryCompressionResult(
                    new ChatTranscriptMemory(compressedMessages, memory.getLastObservedTotalTokens()),
                    true,
                    "compressed"
            );
        } catch (RuntimeException exception) {
            // 压缩失败时直接回退原始 transcript，不能让保护机制反过来中断主流程。
            return new MemoryCompressionResult(memory, false, "summarization_failed");
        }
    }

    private boolean shouldCompress(MemoryCompressionRequest request, ChatTranscriptMemory memory) {
        Integer observedTotalTokens = memory.getLastObservedTotalTokens();
        Integer triggerTotalTokens = request.getCompressionTriggerTotalTokens();
        if (observedTotalTokens != null && triggerTotalTokens != null) {
            return observedTotalTokens >= triggerTotalTokens;
        }
        if (observedTotalTokens != null) {
            return observedTotalTokens >= properties.getTriggerTotalTokens();
        }
        Integer fallbackMaxMessageCount = request.getFallbackMaxMessageCount();
        if (fallbackMaxMessageCount == null) {
            fallbackMaxMessageCount = properties.getFallbackMaxMessageCount();
        }
        return fallbackMaxMessageCount != null && memory.getMessages().size() > fallbackMaxMessageCount;
    }

    private List<String> summarizeWithModel(List<ChatMessage> messages) {
        String transcript = renderMessages(messages);
        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from("""
                                You compress conversation history for a document agent.
                                Summarize the provided transcript into concise memory blocks.
                                Requirements:
                                - preserve user goals, important decisions, tool findings, document changes, unresolved points
                                - do not copy raw tool arguments verbatim unless essential
                                - return plain text only
                                - if multiple blocks help readability, separate them with a line containing only <<<SECTION>>>
                                """),
                        UserMessage.from(transcript)
                ))
                .build());
        if (response == null || response.aiMessage() == null) {
            return List.of();
        }
        String summary = response.aiMessage().text();
        if (summary == null || summary.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(summary.split("\\n<<<SECTION>>>\\n"))
                .map(String::trim)
                .filter(section -> !section.isBlank())
                .toList();
    }

    private static String renderMessages(List<ChatMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : messages) {
            builder.append(message.getClass().getSimpleName())
                    .append(": ")
                    .append(message.getText() == null ? "" : message.getText())
                    .append("\n");
        }
        return builder.toString();
    }

    private String formatCompressedSummary(int sectionIndex, int totalSections, String section) {
        return COMPRESSED_MEMORY_MARKER.formatted(sectionIndex, totalSections) + "\n" + section;
    }
}
