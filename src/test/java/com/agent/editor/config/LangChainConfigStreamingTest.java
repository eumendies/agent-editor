package com.agent.editor.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class LangChainConfigStreamingTest {

    @Test
    void shouldCreateBlockingAndStreamingChatModelsFromSameConfiguration() {
        LangChainConfig config = new LangChainConfig();
        ReflectionTestUtils.setField(config, "apiKey", "test-key");
        ReflectionTestUtils.setField(config, "modelName", "test-model");
        ReflectionTestUtils.setField(config, "baseUrl", "https://example.com/v1");
        ReflectionTestUtils.setField(config, "temperature", 0.2d);
        ReflectionTestUtils.setField(config, "maxTokens", 256);
        ReflectionTestUtils.setField(config, "embeddingApiKey", "embed-key");
        ReflectionTestUtils.setField(config, "embeddingModelName", "embed-model");
        ReflectionTestUtils.setField(config, "embeddingBaseUrl", "https://example.com/embeddings");

        ChatModel chatModel = config.chatLanguageModel();
        StreamingChatModel streamingChatModel = config.streamingChatLanguageModel();

        assertNotNull(chatModel);
        assertNotNull(streamingChatModel);
    }
}
