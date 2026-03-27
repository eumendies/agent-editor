package com.agent.editor.utils.rag.pdf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class PdfKnowledgeExtractor {

    private static final float ROW_TOLERANCE = 8.0f;
    private static final float COLUMN_GAP = 20.0f;
    // 可见字符太少时，基本可以视为扫描件或图片页，当前版本直接给出 OCR 未启用提示。
    private static final int OCR_MIN_VISIBLE_CHARACTERS = 10;

    private final PdfTextExtractor textExtractor;

    public PdfKnowledgeExtractor() {
        try {
            this.textExtractor = new PdfTextExtractor();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to initialize PDF text extractor", e);
        }
    }

    public String extract(byte[] bytes) throws IOException {
        List<PdfTextLine> extracted = textExtractor.extract(bytes).stream()
                .filter(line -> !line.isBlank())
                .toList();
        if (visibleCharacterCount(extracted) < OCR_MIN_VISIBLE_CHARACTERS) {
            throw new IllegalArgumentException("PDF requires OCR, but OCR is not enabled yet");
        }

        Map<Integer, List<PdfTextLine>> pages = extracted.stream()
                .collect(Collectors.groupingBy(
                        PdfTextLine::getPageIndex,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<String> pageContents = new ArrayList<>();
        for (List<PdfTextLine> pageLines : pages.values()) {
            // 这一版只做高置信度噪声过滤，宁可少删，也不误删正文页。
            if (isNoisePage(pageLines)) {
                continue;
            }
            String pageText = formatPage(pageLines).trim();
            if (!pageText.isBlank()) {
                pageContents.add(pageText);
            }
        }

        String content = String.join("\n\n", pageContents).trim();
        if (content.isBlank()) {
            throw new IllegalArgumentException("PDF requires OCR, but OCR is not enabled yet");
        }
        return content;
    }

    private int visibleCharacterCount(List<PdfTextLine> lines) {
        return lines.stream()
                .map(PdfTextLine::getText)
                .map(text -> text.replaceAll("\\s+", ""))
                .mapToInt(String::length)
                .sum();
    }

    private boolean isNoisePage(List<PdfTextLine> pageLines) {
        String pageText = joinInNaturalOrder(pageLines).toLowerCase(Locale.ROOT);
        long dottedLines = pageLines.stream()
                .map(PdfTextLine::getText)
                .filter(text -> text.contains("...."))
                .count();
        return (pageText.contains("contents") || pageText.contains("目录")) && dottedLines > 0;
    }

    private String formatPage(List<PdfTextLine> pageLines) {
        // 先识别表格，再识别双栏；否则表格行容易被双栏逻辑打散。
//        if (looksLikeTable(pageLines)) {
//            return formatTable(pageLines);
//        }
        if (looksLikeDoubleColumn(pageLines)) {
            return formatDoubleColumn(pageLines);
        }
        return joinInNaturalOrder(pageLines);
    }

    private boolean looksLikeTable(List<PdfTextLine> pageLines) {
        List<List<PdfTextLine>> rows = groupRows(pageLines);
        // 至少两行、每行至少三列时，才把它视为表格，避免普通段落误判。
        long multiCellRows = rows.stream().filter(row -> row.size() >= 3).count();
        return multiCellRows >= 2;
    }

    private String formatTable(List<PdfTextLine> pageLines) {
        return groupRows(pageLines).stream()
                .map(row -> row.stream()
                        .sorted(Comparator.comparing(PdfTextLine::getX))
                        .map(PdfTextLine::getText)
                        .collect(Collectors.joining(" | ")))
                .collect(Collectors.joining("\n"));
    }

    private boolean looksLikeDoubleColumn(List<PdfTextLine> pageLines) {
        if (pageLines.size() < 4) {
            return false;
        }
        float pageWidth = pageLines.get(0).getPageWidth();
        float midpoint = pageWidth / 2.0f;
        List<PdfTextLine> left = pageLines.stream()
                .filter(line -> line.centerX() < midpoint)
                .toList();
        List<PdfTextLine> right = pageLines.stream()
                .filter(line -> line.centerX() >= midpoint)
                .toList();
        if (left.size() < 2 || right.size() < 2) {
            return false;
        }
        float leftMax = left.stream().map(PdfTextLine::getX).max(Float::compare).orElse(0.0f);
        float rightMin = right.stream().map(PdfTextLine::getX).min(Float::compare).orElse(pageWidth);
        // 只有左右文本带之间留白足够明显时，才按双栏重排。
        return rightMin - leftMax > COLUMN_GAP;
    }

    private String formatDoubleColumn(List<PdfTextLine> pageLines) {
        float midpoint = pageLines.get(0).getPageWidth() / 2.0f;
        List<PdfTextLine> left = pageLines.stream()
                .filter(line -> line.centerX() < midpoint)
                .sorted(Comparator.comparing(PdfTextLine::getY).thenComparing(PdfTextLine::getX))
                .toList();
        List<PdfTextLine> right = pageLines.stream()
                .filter(line -> line.centerX() >= midpoint)
                .sorted(Comparator.comparing(PdfTextLine::getY).thenComparing(PdfTextLine::getX))
                .toList();
        List<String> ordered = new ArrayList<>();
        // 阅读顺序固定为“左列从上到下，再右列从上到下”。
        left.forEach(line -> ordered.add(line.getText()));
        right.forEach(line -> ordered.add(line.getText()));
        return String.join("\n", ordered);
    }

    private String joinInNaturalOrder(List<PdfTextLine> pageLines) {
        return pageLines.stream()
                .sorted(Comparator.comparing(PdfTextLine::getY).thenComparing(PdfTextLine::getX))
                .map(PdfTextLine::getText)
                .collect(Collectors.joining("\n"));
    }

    private List<List<PdfTextLine>> groupRows(List<PdfTextLine> pageLines) {
        List<PdfTextLine> sorted = pageLines.stream()
                .sorted(Comparator.comparing(PdfTextLine::getY).thenComparing(PdfTextLine::getX))
                .toList();
        List<List<PdfTextLine>> rows = new ArrayList<>();
        for (PdfTextLine line : sorted) {
            if (rows.isEmpty()) {
                rows.add(new ArrayList<>(List.of(line)));
                continue;
            }
            List<PdfTextLine> currentRow = rows.get(rows.size() - 1);
            // y 坐标足够接近就视为同一行，供表格格式化复用。
            if (Math.abs(currentRow.get(0).getY() - line.getY()) <= ROW_TOLERANCE) {
                currentRow.add(line);
            } else {
                rows.add(new ArrayList<>(List.of(line)));
            }
        }
        return rows;
    }
}
