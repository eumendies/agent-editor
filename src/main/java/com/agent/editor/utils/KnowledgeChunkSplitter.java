package com.agent.editor.utils;

import com.agent.editor.config.RagProperties;
import com.agent.editor.model.KnowledgeChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class KnowledgeChunkSplitter {

    private final RagProperties properties;

    public KnowledgeChunkSplitter(RagProperties properties) {
        this.properties = properties;
    }

    public List<KnowledgeChunk> split(String documentId,
                                      String fileName,
                                      String content,
                                      Map<String, String> metadata) {
        List<String> sections = Arrays.stream(content.split("(?m)^# "))
                .filter(section -> !section.isBlank())
                .map(String::trim)
                .toList();

        if (sections.isEmpty()) {
            return List.of(new KnowledgeChunk(documentId, 0, fileName, null, content, metadata));
        }

        List<KnowledgeChunk> result = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            String section = sections.get(i);
            String chunkText = section.length() > properties.chunkSize()
                    ? section.substring(0, properties.chunkSize())
                    : section;
            result.add(new KnowledgeChunk(
                    documentId,
                    i,
                    fileName,
                    extractHeading(section),
                    chunkText,
                    metadata
            ));
        }
        return result;
    }

    private String extractHeading(String section) {
        int newlineIndex = section.indexOf('\n');
        if (newlineIndex < 0) {
            return section.trim();
        }
        return section.substring(0, newlineIndex).trim();
    }
}
