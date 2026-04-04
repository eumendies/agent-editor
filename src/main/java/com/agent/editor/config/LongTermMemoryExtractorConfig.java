package com.agent.editor.config;

import com.agent.editor.agent.v2.memory.LongTermMemoryExtractionAiService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LongTermMemoryExtractorConfig {

    @Bean
    @Qualifier("longTermMemoryExtractorChatModel")
    public ChatModel longTermMemoryExtractorChatModel(
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String defaultModelName,
            @Value("${langchain4j.open-ai.chat-model.max-tokens:4000}") int defaultMaxTokens,
            @Value("${agent.long-term-memory.extractor.model-name:${langchain4j.open-ai.chat-model.model-name}}") String modelName,
            @Value("${agent.long-term-memory.extractor.temperature:${langchain4j.open-ai.chat-model.temperature:0.7}}") double temperature,
            @Value("${agent.long-term-memory.extractor.max-tokens:${langchain4j.open-ai.chat-model.max-tokens:4000}}") int maxTokens) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(hasText(modelName) ? modelName : defaultModelName)
                .temperature(temperature)
                .maxTokens(maxTokens > 0 ? maxTokens : defaultMaxTokens)
                .build();
    }

    @Bean
    public LongTermMemoryExtractionAiService longTermMemoryExtractionAiService(
            @Qualifier("longTermMemoryExtractorChatModel") ChatModel chatModel) {
        return AiServices.builder(LongTermMemoryExtractionAiService.class)
                .chatModel(chatModel)
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
