package com.agent.editor.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "rag.chunk-size=500",
        "rag.chunk-overlap=80",
        "rag.ask-top-k=5"
})
class RagPropertiesTest {

    @Autowired
    private RagProperties ragProperties;

    @Test
    void shouldBindRagProperties() {
        assertEquals(500, ragProperties.getChunkSize());
        assertEquals(80, ragProperties.getChunkOverlap());
        assertEquals(5, ragProperties.getAskTopK());
    }
}
