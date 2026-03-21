package com.agent.editor.utils.pdf;

record PdfTextLine(
        int pageIndex,
        float pageWidth,
        float x,
        float y,
        float width,
        String text
) {

    float centerX() {
        // 双栏判断只需要粗粒度中心点，不需要更复杂的块模型。
        return x + (width / 2.0f);
    }

    boolean isBlank() {
        return text == null || text.isBlank();
    }
}
