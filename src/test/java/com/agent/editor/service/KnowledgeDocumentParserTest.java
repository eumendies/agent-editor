package com.agent.editor.service;

import com.agent.editor.model.ParsedKnowledgeDocument;
import com.agent.editor.utils.KnowledgeDocumentParser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeDocumentParserTest {

    @Test
    void shouldParseMarkdownWithoutExternalOcr() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.md",
                "text/markdown",
                "# 项目\n内容".getBytes(StandardCharsets.UTF_8)
        );
        KnowledgeDocumentParser parser = new KnowledgeDocumentParser();

        ParsedKnowledgeDocument parsed = parser.parse(file);

        assertTrue(parsed.content().contains("项目"));
        assertEquals("MARKDOWN", parsed.documentType());
    }
}
