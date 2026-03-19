package com.agent.editor.service;

import com.agent.editor.config.RagProperties;
import com.agent.editor.model.KnowledgeChunk;
import com.agent.editor.model.KnowledgeDocument;
import com.agent.editor.model.ParsedKnowledgeDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KnowledgeBaseService {

    private final InMemoryKnowledgeStore store;
    private final KnowledgeDocumentParser parser;
    private final KnowledgeChunkSplitter splitter;
    private final RagProperties ragProperties;

    public KnowledgeBaseService(InMemoryKnowledgeStore store,
                                KnowledgeDocumentParser parser,
                                KnowledgeChunkSplitter splitter,
                                RagProperties ragProperties) {
        this.store = store;
        this.parser = parser;
        this.splitter = splitter;
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
        if (parser != null && splitter != null) {
            ParsedKnowledgeDocument parsed = parser.parse(file);
            List<KnowledgeChunk> chunks = splitter.split(
                    document.id(),
                    document.fileName(),
                    parsed.content(),
                    Map.of("category", category, "documentType", parsed.documentType())
            );
            chunks.forEach(store::saveChunk);
        }
        return document;
    }
}
