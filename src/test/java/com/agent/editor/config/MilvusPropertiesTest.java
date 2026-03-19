package com.agent.editor.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "milvus.host=localhost",
        "milvus.port=19530",
        "milvus.collection-name=knowledge_chunks",
        "milvus.embedding-dimension=1024"
})
class MilvusPropertiesTest {

    @Autowired
    private MilvusProperties milvusProperties;

    @Test
    void shouldBindMilvusProperties() {
        assertEquals("localhost", milvusProperties.host());
        assertEquals(19530, milvusProperties.port());
        assertEquals("knowledge_chunks", milvusProperties.collectionName());
        assertEquals(1024, milvusProperties.embeddingDimension());
    }
}
