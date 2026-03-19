package com.agent.editor.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeEmbeddingServiceTest {

    @Test
    void shouldReturnEmbeddingVectorForText() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed("Spring Boot 项目经验"))
                .thenReturn(Response.from(Embedding.from(new float[]{0.1f, 0.2f, 0.3f})));
        KnowledgeEmbeddingService service = new KnowledgeEmbeddingService(embeddingModel);

        float[] vector = service.embed("Spring Boot 项目经验");

        assertArrayEquals(new float[]{0.1f, 0.2f, 0.3f}, vector);
    }
}
