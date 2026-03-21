package com.agent.editor.utils.pdf;

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
    private final List<PdfTextLine> lines = new ArrayList<>();
    private final List<Float> pageWidths = new ArrayList<>();
    private int currentPageIndex;

    PdfTextExtractor() throws IOException {
        setSortByPosition(false);
    }

    List<PdfTextLine> extract(byte[] bytes) throws IOException {
        lines.clear();
        pageWidths.clear();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            for (PDPage page : document.getPages()) {
                pageWidths.add(page.getMediaBox().getWidth());
            }
            getText(document);
        }
        return List.copyOf(lines);
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
        lines.add(new PdfTextLine(
                currentPageIndex,
                pageWidths.get(currentPageIndex),
                first.getXDirAdj(),
                first.getYDirAdj(),
                width,
                text.trim()
        ));
    }
}
