package com.agent.editor.utils.rag.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class PdfTextExtractor extends PDFTextStripper {

    // 这里先保留最基础的“行 + 坐标”信息，后续双栏和表格判断都依赖这些几何数据。
    private static final float SAME_LINE_TOLERANCE = 4.0f;
    private static final float WORD_GAP_TOLERANCE = 60.0f;

    private final List<PdfTextLine> fragments = new ArrayList<>();
    private final List<Float> pageWidths = new ArrayList<>();
    private int currentPageIndex;

    PdfTextExtractor() throws IOException {
        setSortByPosition(false);
    }

    List<PdfTextLine> extract(byte[] bytes) throws IOException {
        fragments.clear();
        pageWidths.clear();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            for (PDPage page : document.getPages()) {
                pageWidths.add(page.getMediaBox().getWidth());
            }
            getText(document);
        }
        return mergeFragmentsIntoLines();
    }

    @Override
    protected void startPage(PDPage page) throws IOException {
        super.startPage(page);
        currentPageIndex = getCurrentPageNo() - 1;
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        if (text == null || text.isBlank() || textPositions.isEmpty()) {
            return;
        }
        TextPosition first = textPositions.get(0);
        TextPosition last = textPositions.get(textPositions.size() - 1);
        // 用首尾字符位置估算整行宽度，当前版本不追求字级精确布局。
        float width = Math.max(0, (last.getXDirAdj() + last.getWidthDirAdj()) - first.getXDirAdj());
        fragments.add(new PdfTextLine(
                currentPageIndex,
                pageWidths.get(currentPageIndex),
                first.getXDirAdj(),
                first.getYDirAdj(),
                width,
                text.trim()
        ));
    }

    private List<PdfTextLine> mergeFragmentsIntoLines() {
        List<PdfTextLine> sorted = fragments.stream()
                .sorted((left, right) -> {
                    int pageCompare = Integer.compare(left.pageIndex(), right.pageIndex());
                    if (pageCompare != 0) {
                        return pageCompare;
                    }
                    int yCompare = Float.compare(left.y(), right.y());
                    if (yCompare != 0) {
                        return yCompare;
                    }
                    return Float.compare(left.x(), right.x());
                })
                .toList();

        List<PdfTextLine> merged = new ArrayList<>();
        for (PdfTextLine fragment : sorted) {
            if (merged.isEmpty()) {
                merged.add(fragment);
                continue;
            }

            PdfTextLine previous = merged.get(merged.size() - 1);
            if (shouldMerge(previous, fragment)) {
                merged.set(merged.size() - 1, merge(previous, fragment));
            } else {
                merged.add(fragment);
            }
        }
        return List.copyOf(merged);
    }

    private boolean shouldMerge(PdfTextLine previous, PdfTextLine current) {
        if (previous.pageIndex() != current.pageIndex()) {
            return false;
        }
        if (Math.abs(previous.y() - current.y()) > SAME_LINE_TOLERANCE) {
            return false;
        }
        float previousEnd = previous.x() + previous.width();
        float gap = current.x() - previousEnd;
        return gap >= 0 && gap <= WORD_GAP_TOLERANCE;
    }

    private PdfTextLine merge(PdfTextLine previous, PdfTextLine current) {
        float endX = Math.max(previous.x() + previous.width(), current.x() + current.width());
        return new PdfTextLine(
                previous.pageIndex(),
                previous.pageWidth(),
                previous.x(),
                previous.y(),
                endX - previous.x(),
                previous.text() + " " + current.text()
        );
    }
}
