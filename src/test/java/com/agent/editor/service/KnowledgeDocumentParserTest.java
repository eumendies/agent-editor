package com.agent.editor.service;

import com.agent.editor.model.ParsedKnowledgeDocument;
import com.agent.editor.utils.rag.KnowledgeDocumentParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        assertTrue(parsed.getContent().contains("项目"));
        assertEquals("MARKDOWN", parsed.getDocumentType());
    }

    @Test
    void shouldParsePdfDocument() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "knowledge.pdf",
                "application/pdf",
                createPdf("Knowledge Parser PDF Sample", 72, 720)
        );
        KnowledgeDocumentParser parser = new KnowledgeDocumentParser();

        ParsedKnowledgeDocument parsed = parser.parse(file);

        assertEquals("PDF", parsed.getDocumentType());
        assertTrue(parsed.getContent().contains("Knowledge Parser PDF Sample"));
    }

    @Test
    void shouldRejectUnsupportedBinaryFormat() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "knowledge.bin",
                "application/octet-stream",
                new byte[]{1, 2, 3}
        );
        KnowledgeDocumentParser parser = new KnowledgeDocumentParser();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> parser.parse(file));

        assertTrue(error.getMessage().contains("Unsupported knowledge document format"));
    }

    @Test
    void shouldReadDoubleColumnPdfInColumnOrder() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "double-column.pdf",
                "application/pdf",
                createDoubleColumnPdf()
        );
        KnowledgeDocumentParser parser = new KnowledgeDocumentParser();

        ParsedKnowledgeDocument parsed = parser.parse(file);

        String content = parsed.getContent();
        assertTrue(content.indexOf("Left Column A") < content.indexOf("Left Column B"));
        assertTrue(content.indexOf("Left Column B") < content.indexOf("Right Column A"));
    }

    @Test
    void shouldRemoveTableOfContentsNoiseFromPdf() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "toc.pdf",
                "application/pdf",
                createTableOfContentsPdf()
        );
        KnowledgeDocumentParser parser = new KnowledgeDocumentParser();

        ParsedKnowledgeDocument parsed = parser.parse(file);

        assertFalse(parsed.getContent().contains("Contents"));
        assertFalse(parsed.getContent().contains("Chapter One........1"));
        assertTrue(parsed.getContent().contains("Chapter One Body"));
    }

    @Test
    void shouldReadSimpleTablePdfInNaturalOrderWhenTableFormattingIsDisabled() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "table.pdf",
                "application/pdf",
                createSimpleTablePdf()
        );
        KnowledgeDocumentParser parser = new KnowledgeDocumentParser();

        ParsedKnowledgeDocument parsed = parser.parse(file);

        String content = parsed.getContent();
        assertTrue(content.contains("Name"), content);
        assertTrue(content.contains("Score"), content);
        assertTrue(content.contains("Rank"), content);
        assertTrue(content.contains("Alice"), content);
        assertTrue(content.contains("95"), content);
        assertTrue(content.contains("1"), content);
        assertFalse(content.contains("Name | Score | Rank"), content);
    }

    @Test
    void shouldKeepNormalSentencePdfAsPlainTextInsteadOfTable() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sentence.pdf",
                "application/pdf",
                createSegmentedSentencePdf()
        );
        KnowledgeDocumentParser parser = new KnowledgeDocumentParser();

        ParsedKnowledgeDocument parsed = parser.parse(file);

        assertTrue(parsed.getContent().contains("Alpha beta gamma"), parsed.getContent());
        assertTrue(parsed.getContent().contains("Delta epsilon zeta"), parsed.getContent());
        assertFalse(parsed.getContent().contains("Alpha | beta | gamma"), parsed.getContent());
        assertFalse(parsed.getContent().contains("Delta | epsilon | zeta"), parsed.getContent());
    }

    @Test
    void shouldFailGracefullyWhenPdfNeedsOcr() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "scan.pdf",
                "application/pdf",
                createImageOnlyPdf()
        );
        KnowledgeDocumentParser parser = new KnowledgeDocumentParser();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> parser.parse(file));

        assertTrue(error.getMessage().contains("OCR"));
    }

    private byte[] createPdf(String text, float x, float y) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(x, y);
                contentStream.showText(text);
                contentStream.endText();
            }

            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createDoubleColumnPdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                writeLine(contentStream, "Left Column A", 72, 720);
                writeLine(contentStream, "Right Column A", 320, 720);
                writeLine(contentStream, "Left Column B", 72, 690);
                writeLine(contentStream, "Right Column B", 320, 690);
            }

            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createTableOfContentsPdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage tocPage = new PDPage(PDRectangle.LETTER);
            document.addPage(tocPage);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, tocPage)) {
                writeLine(contentStream, "Contents", 72, 720);
                writeLine(contentStream, "Chapter One........1", 72, 690);
                writeLine(contentStream, "Chapter Two........5", 72, 660);
            }

            PDPage bodyPage = new PDPage(PDRectangle.LETTER);
            document.addPage(bodyPage);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, bodyPage)) {
                writeLine(contentStream, "Chapter One Body", 72, 720);
                writeLine(contentStream, "Real body paragraph.", 72, 690);
            }

            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createSimpleTablePdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                writeRow(contentStream, List.of("Name", "Score", "Rank"), 720);
                writeRow(contentStream, List.of("Alice", "95", "1"), 690);
            }

            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createImageOnlyPdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < image.getWidth(); x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    image.setRGB(x, y, 0xFFFFFF);
                }
            }
            PDImageXObject imageObject = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.drawImage(imageObject, 72, 600, 120, 120);
            }

            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createSegmentedSentencePdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                writeWord(contentStream, "Alpha", 72, 720);
                writeWord(contentStream, "beta", 120, 720);
                writeWord(contentStream, "gamma", 160, 720);
                writeWord(contentStream, "Delta", 72, 690);
                writeWord(contentStream, "epsilon", 120, 690);
                writeWord(contentStream, "zeta", 180, 690);
            }

            document.save(output);
            return output.toByteArray();
        }
    }

    private void writeRow(PDPageContentStream contentStream, List<String> values, float y) throws IOException {
        float[] xPositions = {72, 220, 340};
        for (int i = 0; i < values.size(); i++) {
            writeLine(contentStream, values.get(i), xPositions[i], y);
        }
    }

    private void writeLine(PDPageContentStream contentStream, String text, float x, float y) throws IOException {
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(text);
        contentStream.endText();
    }

    private void writeWord(PDPageContentStream contentStream, String text, float x, float y) throws IOException {
        writeLine(contentStream, text, x, y);
    }
}
