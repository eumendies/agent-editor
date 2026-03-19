package com.agent.editor.service;

import com.agent.editor.config.RagProperties;
import com.agent.editor.model.KnowledgeChunk;
import com.agent.editor.model.KnowledgeDocument;
import com.agent.editor.model.ParsedKnowledgeDocument;
import com.agent.editor.repository.InMemoryKnowledgeStore;
import com.agent.editor.repository.KnowledgeChunkRepository;
import com.agent.editor.utils.KnowledgeChunkSplitter;
import com.agent.editor.utils.KnowledgeDocumentParser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KnowledgeBaseService {

    private final InMemoryKnowledgeStore store;
    private final KnowledgeChunkRepository repository;
    private final KnowledgeDocumentParser parser;
    private final KnowledgeChunkSplitter splitter;
    private final KnowledgeEmbeddingService embeddingService;
    private final RagProperties ragProperties;

    public KnowledgeBaseService(InMemoryKnowledgeStore store,
                                KnowledgeChunkRepository repository,
                                KnowledgeDocumentParser parser,
                                KnowledgeChunkSplitter splitter,
                                KnowledgeEmbeddingService embeddingService,
                                RagProperties ragProperties) {
        this.store = store;
        this.repository = repository;
        this.parser = parser;
        this.splitter = splitter;
        this.embeddingService = embeddingService;
        this.ragProperties = ragProperties;
    }

    public KnowledgeDocument upload(MultipartFile file, String category) {
        KnowledgeDocument document = new KnowledgeDocument(
                UUID.randomUUID().toString(),
                file.getOriginalFilename(),
                category,
                "PENDING",
                Instant.now()
        );
        store.saveDocument(document);
        if (parser != null && splitter != null && repository != null) {
            ParsedKnowledgeDocument parsed = parser.parse(file);
            List<KnowledgeChunk> chunks = splitter.split(
                    document.id(),
                    document.fileName(),
                    parsed.content(),
                    Map.of("category", category, "documentType", parsed.documentType())
            );
            List<KnowledgeChunk> chunksToPersist = embeddingService == null
                    ? chunks
                    : chunks.stream()
                    .map(chunk -> chunk.withEmbedding(embeddingService.embed(chunk.chunkText())))
                    .toList();
            repository.saveAll(chunksToPersist);
        }
        return document;
    }
}
