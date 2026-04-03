package com.agent.editor.utils.rag.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Uses OpenDataLoader to extract Markdown from uploaded PDF bytes.
 */
public class OpenDataLoaderPdfKnowledgeExtractor {

    /**
     * Extracts readable text from a PDF upload.
     *
     * @param bytes PDF file bytes
     * @return extracted Markdown content
     */
    public String extract(byte[] bytes) {
        Path inputFile = null;
        Path outputDirectory = null;
        try {
            // ODL 在纯图片 PDF 上会直接触发 JVM abort，这里先用轻量文本探测把扫描件短路成受控失败。
            if (!hasExtractableText(bytes)) {
                throw new IllegalArgumentException("PDF contains no extractable text");
            }

            inputFile = Files.createTempFile("knowledge-upload-", ".pdf");
            outputDirectory = Files.createTempDirectory("knowledge-output-");
            Files.write(inputFile, bytes);

            Config config = new Config();
            config.setGenerateMarkdown(true);
            config.setOutputFolder(outputDirectory.toString());
            config.setHybrid(Config.HYBRID_OFF);
            config.setOutputStdout(false);

            OpenDataLoaderPDF.processFile(inputFile.toString(), config);

            String markdown = readMarkdownOutput(outputDirectory).trim();
            if (markdown.isBlank()) {
                throw new IllegalArgumentException("PDF contains no extractable text");
            }
            return markdown;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Failed to extract PDF content", e);
        } finally {
            OpenDataLoaderPDF.shutdown();
            deleteIfExists(inputFile);
            deleteRecursively(outputDirectory);
        }
    }

    private boolean hasExtractableText(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            return !text.isBlank();
        }
    }

    // OpenDataLoader 会把结果写到输出目录里，这里按扩展名查找 Markdown 结果而不是依赖固定文件名。
    private String readMarkdownOutput(Path outputDirectory) throws IOException {
        try (Stream<Path> paths = Files.walk(outputDirectory)) {
            Path markdownFile = paths
                    .filter(Files::isRegularFile)
                    .filter(this::isMarkdownFile)
                    .sorted(Comparator.naturalOrder())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("PDF contains no extractable text"));
            return Files.readString(markdownFile, StandardCharsets.UTF_8);
        }
    }

    private boolean isMarkdownFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".md") || fileName.endsWith(".markdown");
    }

    // 输出目录里可能包含图片等附属文件，按倒序删除能避免目录非空异常。
    private void deleteRecursively(Path root) {
        if (root == null || Files.notExists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::deleteIfExists);
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }

    private void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }
}
