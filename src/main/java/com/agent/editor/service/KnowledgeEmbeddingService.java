package com.agent.editor.service;

import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeEmbeddingService {

    private final EmbeddingModel embeddingModel;

    public KnowledgeEmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public float[] embed(String text) {
        return embeddingModel.embed(text).content().vector();
    }
}
