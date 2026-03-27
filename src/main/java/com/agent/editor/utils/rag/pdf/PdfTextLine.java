package com.agent.editor.utils.rag.pdf;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
class PdfTextLine {

    private int pageIndex;
    private float pageWidth;
    private float x;
    private float y;
    private float width;
    private String text;

    float centerX() {
        // 双栏判断只需要粗粒度中心点，不需要更复杂的块模型。
        return x + (width / 2.0f);
    }

    boolean isBlank() {
        return text == null || text.isBlank();
    }
}
