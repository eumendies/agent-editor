package com.agent.editor.service;

import com.agent.editor.config.RagProperties;
import com.agent.editor.model.KnowledgeDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

@Service
public class KnowledgeBaseService {

    private final InMemoryKnowledgeStore store;
    private final Object parser;
    private final Object splitter;
    private final RagProperties ragProperties;

    public KnowledgeBaseService(InMemoryKnowledgeStore store,
                                Object parser,
                                Object splitter,
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
        return document;
    }
}
