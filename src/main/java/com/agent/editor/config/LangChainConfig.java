package com.agent.editor.config;

import com.agent.editor.agent.event.EventPublisher;
import com.agent.editor.agent.model.StreamingLLMInvoker;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class LangChainConfig {
    
    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;
    
    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String modelName;
    
    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;
    
    @Value("${langchain4j.open-ai.chat-model.temperature:0.7}")
    private double temperature;
    
    @Value("${langchain4j.open-ai.chat-model.max-tokens:4000}")
    private int maxTokens;

    @Value("${langchain4j.open-ai.embedding-model.api-key}")
    private String embeddingApiKey;

    @Value("${langchain4j.open-ai.embedding-model.model-name}")
    private String embeddingModelName;

    @Value("${langchain4j.open-ai.embedding-model.base-url}")
    private String embeddingBaseUrl;

    @Bean
    @Primary
    public ChatModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
    }

    @Bean
    public StreamingChatModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .temperature(temperature)
                .maxCompletionTokens(maxTokens)
                .build();
    }

    @Bean
    public StreamingLLMInvoker streamingDecisionInvoker(StreamingChatModel streamingChatLanguageModel,
                                                        EventPublisher eventPublisher) {
        return new StreamingLLMInvoker(streamingChatLanguageModel, eventPublisher);
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(embeddingApiKey)
                .modelName(embeddingModelName)
                .baseUrl(embeddingBaseUrl)
                .build();
    }
}
